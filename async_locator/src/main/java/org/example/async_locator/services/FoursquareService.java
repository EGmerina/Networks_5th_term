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

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class FoursquareService {
    private static final Logger logger = LogManager.getLogger(FoursquareService.class);
    private static final String BASE_URL = "https://places-api.foursquare.com/places/search";

    private final AsyncHttpClient client;
    private final AsyncLoadingCache<Location, CompletableFuture<ArrayList<Place>>> cache;

    public FoursquareService() {
        this.client = new AsyncHttpClient();

        // Настраиваем кэш (5 минут жизни, максимум 1000 записей)
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .buildAsync(this::loadFromFoursquare);
    }

    /**
     * Основной метод получения мест — берёт из кэша или запрашивает у API
     */
    public CompletableFuture<ArrayList<Place>> getPlaces(Location location) {
        return cache.get(location).thenCompose(f -> f);
    }

    /**
     * Загружает реальные данные из Foursquare API
     */
    private CompletableFuture<ArrayList<Place>> loadFromFoursquare(Location location) {
        String url = String.format(
                "https://places-api.foursquare.com/places/search?ll=%.6f,%.6f&radius=1000&limit=10&categories=16000,13065",
                location.lat(), location.lon()
        );

        logger.info("Запрос к Foursquare (new host) API: {}", url);

        return client.getWithAuth(url, ApiKeys.FORSQUARE_KEY)
                .thenApply(this::safeParsePlaces)
                .exceptionally(ex -> {
                    logger.error("Ошибка при запросе к Foursquare: {}", ex.getMessage(), ex);
                    return new ArrayList<>();
                });
    }

    /**
     * Проверяет, что ответ действительно JSON и не пустой
     */
    private ArrayList<Place> safeParsePlaces(String body) {
        if (body == null || body.isBlank()) {
            logger.error("Пустой ответ от Foursquare");
            return new ArrayList<>();
        }

        if (!body.trim().startsWith("{")) {
            logger.error("Foursquare вернул НЕ JSON. Ответ:\n{}", body.substring(0, Math.min(body.length(), 500)));
            return new ArrayList<>();
        }

        return parsePlaces(body);
    }

    /**
     * Разбирает JSON и создаёт список мест
     */
    private ArrayList<Place> parsePlaces(String body) {
        ArrayList<Place> places = new ArrayList<>();
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        JsonArray results = root.getAsJsonArray("results");
        if (results == null) return places;

        for (JsonElement el : results) {
            JsonObject obj = el.getAsJsonObject();

            String name = obj.has("name") ? obj.get("name").getAsString() : "Без названия";
            String fsqId = obj.has("fsq_place_id") ? obj.get("fsq_place_id").getAsString()
                    : (obj.has("fsq_id") ? obj.get("fsq_id").getAsString() : null);
            String address = "Адрес неизвестен";

            if (obj.has("location")) {
                JsonObject loc = obj.getAsJsonObject("location");
                if (loc.has("formatted_address"))
                    address = loc.get("formatted_address").getAsString();
            }

            places.add(new Place(fsqId, name, address));
        }

        return places;
    }

    public CompletableFuture<String> getDescription(String fsqId) {
        if (fsqId == null || fsqId.isBlank()) {
            return CompletableFuture.completedFuture("Описание недоступно");
        }

        String url = String.format("https://places-api.foursquare.com/places/%s", fsqId);

        logger.info("Запрос описания к Foursquare API: {}", url);

        return client.getWithAuth(url, ApiKeys.FORSQUARE_KEY)
                .thenApply(response -> {
                    logger.info("Ответ от Foursquare: {}", response);
                    return parseDescription(response);
                })
                .exceptionally(ex -> {
                    logger.error("Ошибка при получении описания Foursquare: {}", ex.getMessage(), ex);
                    return "Описание недоступно";
                });
    }

    private String parseDescription(String body) {

        JsonObject obj = JsonParser.parseString(body).getAsJsonObject();
        String name = obj.has("name") ? obj.get("name").getAsString() : "Неизвестное место";

        String category = "";
        if (obj.has("categories")) {
            JsonArray cats = obj.getAsJsonArray("categories");
            if (!cats.isEmpty()) {
                category = cats.get(0).getAsJsonObject().get("name").getAsString();
            }
        }

        String address = "";
        if (obj.has("location")) {
            JsonObject loc = obj.getAsJsonObject("location");
            if (loc.has("formatted_address")) {
                address = loc.get("formatted_address").getAsString();
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append(name);
        if (!category.isEmpty()) sb.append(" — ").append(category);
        if (!address.isEmpty()) sb.append(". Адрес: ").append(address);

        if (obj.has("website")) sb.append(". Сайт: ").append(obj.get("website").getAsString());
        if (obj.has("tel")) sb.append(". Телефон: ").append(obj.get("tel").getAsString());

        return sb.toString();

    }

}
