package org.example.async_locator.services;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.async_locator.AsyncLocationExplorer;
import org.example.async_locator.models.Location;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class GraphHopperService {
    private static final String KEY = "9d378a34-fc8d-49a5-ae5c-c5d002235b5a";
    private final AsyncHttpClient client;
    private static final Logger logger = LogManager.getLogger(GraphHopperService.class);
    private final AsyncLoadingCache<String, CompletableFuture<ArrayList<Location>>> cache;
    private static final int MAX_NUM_LOCATIONS_REC = 15;

    public GraphHopperService() {
        this.client = new AsyncHttpClient();

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .buildAsync(this::loadFromGraphHopper);
    }

    public CompletableFuture<ArrayList<Location>> getLocations(String query) {
        String normalizedQuery = query.trim().toLowerCase();
        return cache.get(normalizedQuery).thenCompose(f -> f);
    }

    private CompletableFuture<ArrayList<Location>> loadFromGraphHopper(String query) {
        String url = String.format("https://graphhopper.com/api/1/geocode?q=%s&limit=%s&key=%s", query, MAX_NUM_LOCATIONS_REC, KEY);
        logger.info("Запрос к GraphHopper API: {}", url);

        return client.get(url)
                .thenApply(this::parseLocations)
                .exceptionally(ex -> {
                    logger.error("Ошибка при запросе GraphHopper: {}", ex.getMessage(), ex);
                    return new ArrayList<Location>();
                });
    }


    private ArrayList<Location> parseLocations(String body) {
        ArrayList<Location> locations = new ArrayList<>();

        JsonArray hits = JsonParser.parseString(body)
                .getAsJsonObject()
                .getAsJsonArray("hits");
        for (JsonElement e : hits) {
            JsonObject o = e.getAsJsonObject();
            String name = o.has("name") ? o.get("name").getAsString() : "Без названия";
            JsonObject point = o.getAsJsonObject("point");
            locations.add(new Location(
                    name,
                    point.get("lat").getAsDouble(),
                    point.get("lng").getAsDouble()
            ));
        }

        return locations;
    }

}
