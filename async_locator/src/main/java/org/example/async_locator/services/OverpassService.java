package org.example.async_locator.services;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.async_locator.models.Location;
import org.example.async_locator.models.Place;


import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class OverpassService {
    private final AsyncHttpClient client;
    private static final Logger logger = LogManager.getLogger(OverpassService.class);
    private final AsyncLoadingCache<Location, CompletableFuture<ArrayList<Place>>> cache;

    public OverpassService() {
        this.client = new AsyncHttpClient();

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .buildAsync(this::loadFromOverpass);
    }

    public CompletableFuture<ArrayList<Place>> getPlaces(Location location) {
        return cache.get(location).thenCompose(f -> f);
    }

    private CompletableFuture<ArrayList<Place>> loadFromOverpass(Location location) {

        String query = String.format(
                "[out:json];" +
                        "node(around:%d,%.6f,%.6f)" +
                        "[\"tourism\"~\"museum|attraction|artwork|gallery|zoo|theme_park\"]" +
                        "[\"wikidata\"];" +
                        "out;",
                500, location.lat(), location.lon()
        );

        String url = "https://z.overpass-api.de/api/interpreter";

        String payload = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);

        logger.info("Запрос к Overpass API (POST): {}", url);

        return client.post(url, payload)
                .thenApply(this::safeParsePlaces)
                .exceptionally(ex -> {
                    logger.error("Ошибка при запросе к Overpass: {}", ex.getMessage(), ex);
                    return new ArrayList<>();
                });
    }

    private ArrayList<Place> safeParsePlaces(String body) {

        if (body == null || body.isBlank()) {
            logger.error("Пустой ответ от Overpass");
            return new ArrayList<>();
        }


        if (!body.trim().startsWith("{")) {
            logger.error("Overpass вернул НЕ JSON. Ответ:\n{}",
                    body.substring(0, Math.min(body.length(), 500)));
            return new ArrayList<>();
        }

        return parsePlaces(body);
    }

    public CompletableFuture<String> getDescription(String wikipediaTag) {
        if (wikipediaTag == null) {
            return CompletableFuture.completedFuture(null);
        }

        String url = String.format("https://www.wikidata.org/wiki/Special:EntityData/%s.json", wikipediaTag);


        return client.get(url)
                .thenApply(body -> parseDescription(body, wikipediaTag))
                .exceptionally(ex -> {
                    logger.error("Ошибка при запросе description: {}, tag {}", ex.getMessage(), ex, wikipediaTag);
                    return null;
                });

    }

    private String parseDescription(String body, String tag) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();

        JsonObject entities = root.getAsJsonObject("entities");
        if (entities == null || !entities.has(tag)) return null;

        JsonObject entity = entities.getAsJsonObject(tag);

        JsonObject descriptions = entity.getAsJsonObject("descriptions");
        if (descriptions == null) return null;

        // Сначала пробуем русский
        if (descriptions.has("ru")) {
            return descriptions.getAsJsonObject("ru").get("value").getAsString();
        }

        // Если нет — пробуем английский
        if (descriptions.has("en")) {
            return descriptions.getAsJsonObject("en").get("value").getAsString();
        }

        return null;
    }


    private ArrayList<Place> parsePlaces(String body) {

        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray elements = root.getAsJsonArray("elements");

        ArrayList<Place> places = new ArrayList<>();
        for (JsonElement el : elements) {
            JsonObject obj = el.getAsJsonObject();
            JsonObject tags = obj.has("tags") ? obj.getAsJsonObject("tags") : null;
            if (tags == null || !tags.has("name")) continue;
            String name = tags.get("name").getAsString();
            String wiki = tags.has("wikidata") ? tags.get("wikidata").getAsString() : null;
            places.add(new Place(wiki, name, null));
        }
        return places;
    }

}
