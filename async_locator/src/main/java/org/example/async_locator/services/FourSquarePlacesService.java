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

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

public class FourSquarePlacesService {
    private final AsyncHttpClient client;
    private static final Logger logger = LogManager.getLogger(FourSquarePlacesService.class);
    private final AsyncLoadingCache<String, CompletableFuture<ArrayList<Place>>> cache;
    private static final int MAX_NUM_LOCATIONS_REC = 10;

    public FourSquarePlacesService() {
        this.client = new AsyncHttpClient();

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .buildAsync(this::loadFromFourSquare);
    }

    public CompletableFuture<ArrayList<Place>> getPlaces(String query) {
        String normalizedQuery = query.trim().toLowerCase();
        return cache.get(normalizedQuery).thenCompose(f -> f);
    }

    private CompletableFuture<ArrayList<Place>> loadFromFourSquare(String query) {
        String url = String.format("https://graphhopper.com/api/1/geocode?q=%s&limit=%s&key=%s", query, MAX_NUM_LOCATIONS_REC, ApiKeys.GRAPHHOPPER_KEY);
        logger.info("Запрос к GraphHopper API: {}", url);

        return client.get(url)
                .thenApply(this::parsePlaces)
                .exceptionally(ex -> {
                    logger.error("Ошибка при запросе GraphHopper: {}", ex.getMessage(), ex);
                    return new ArrayList<Place>();
                });
    }


    private ArrayList<Place> parsePlaces(String body) {
        ArrayList<Place> places = new ArrayList<>();

//TODO
        return places;
    }
}
