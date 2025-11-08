package org.example.async_locator.services;

import com.github.benmanes.caffeine.cache.AsyncLoadingCache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.example.async_locator.models.Location;
import org.example.async_locator.models.Weather;

import java.time.Duration;

import java.util.concurrent.CompletableFuture;

public class OpenWeatherService {
    private final AsyncHttpClient client;
    private static final Logger logger = LogManager.getLogger(OpenWeatherService.class);
    private final AsyncLoadingCache<Location, CompletableFuture<Weather>> cache;

    public OpenWeatherService() {
        this.client = new AsyncHttpClient();

        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(5))
                .maximumSize(1000)
                .buildAsync(this::loadFromOpenWeather);
    }

    public CompletableFuture<Weather> getWeather(Location location) {
        return cache.get(location).thenCompose(f -> f);
    }

    private CompletableFuture<Weather> loadFromOpenWeather(Location location) {
        String url = String.format("https://api.openweathermap.org/data/2.5/weather?lat=%s&lon=%s&appid=%s", location.lat(), location.lon(), ApiKeys.OPENWEATHER_KEY);
        logger.info("Запрос к OpenWeather API: {}", url);

        return client.get(url)
                .thenApply(this::parseWeather)
                .exceptionally(ex -> {
                    logger.error("Ошибка при запросе OpenWeather: {}", ex.getMessage(), ex);
                    return new Weather(0, "can't get weather");
                });
    }


    private Weather parseWeather(String body) {

        JsonObject jsonObject = JsonParser.parseString(body).getAsJsonObject();
        JsonObject main = jsonObject.getAsJsonObject("main");
        double temp = main.has("temp") ? main.get("temp").getAsDouble() : 0;
        JsonArray weatherArray = jsonObject.getAsJsonArray("weather");
        String description = "without description";
        if (weatherArray != null && weatherArray.size() > 0) {
            JsonObject firstWeather = weatherArray.get(0).getAsJsonObject();
            if (firstWeather.has("description")) {
                description = firstWeather.get("description").getAsString();
            }
        }

        return new Weather(temp, description);
    }

}
