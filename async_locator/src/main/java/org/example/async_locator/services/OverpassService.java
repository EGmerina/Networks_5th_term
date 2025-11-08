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
import org.example.async_locator.models.Weather;

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
        String query = String.format("[out:json];node(around:1000,%f,%f)[tourism~\"museum|attraction|artwork|gallery|monument\"];out;", location.lat(), location.lon());
        String url = "https://overpass-api.de/api/interpreter?data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        logger.info("Запрос к Overpass API: {}", url);

        return client.get(url)
                .thenApply(this::parsePlaces)
                .exceptionally(ex -> {
                    logger.error("Ошибка при запросе к Overpass: {}", ex.getMessage(), ex);
                    return new ArrayList<Place>();
                });
    }

    public CompletableFuture<String> getDescription(String wikipediaTag) {
        if (wikipediaTag == null) {
            return CompletableFuture.completedFuture("Описание недоступно");
        }

        String[] parts = wikipediaTag.split(":");
        if (parts.length < 2) {
            return CompletableFuture.completedFuture("Некорректная ссылка Wikipedia");
        }

        String lang = parts[0];
        String title = URLEncoder.encode(parts[1], StandardCharsets.UTF_8);
        String url = String.format("https://%s.wikipedia.org/api/rest_v1/page/summary/%s", lang, title);


        return client.get(url)
                .thenApply(this::parseDescription)
                .exceptionally(ex -> {
                    logger.error("Ошибка при запросе к Overpass: {}", ex.getMessage(), ex);
                    return new String("");
                });

    }

    private String parseDescription(String body) {
        JsonObject json = JsonParser.parseString(body).getAsJsonObject();
        return json.has("extract") ? json.get("extract").getAsString() : "Описание отсутствует";
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
            String wiki = tags.has("wikipedia") ? tags.get("wikipedia").getAsString() : null;
            places.add(new Place(name, wiki, null));
        }
        return places;
    }

}
