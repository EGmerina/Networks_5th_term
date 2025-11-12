package org.example.async_locator.services;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

public class AsyncHttpClient {
    private static final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(10)).build();
    private static final Logger logger = LogManager.getLogger(AsyncHttpClient.class);
    private static final int TIMEOUT = 20;

    public CompletableFuture<String> get(String url) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(TIMEOUT))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "TravelInfoApp/1.0 (student project; no website)")
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).thenApply(this::checkStatus).thenApply(HttpResponse::body);
    }

    public CompletableFuture<String> post(String url, String body) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT))
                .header("Accept", "application/json")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("User-Agent", "TravelInfoApp/1.0 (student project; no website)")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::checkStatus)
                .thenApply(HttpResponse::body);
    }

    private HttpResponse<String> checkStatus(HttpResponse<String> response) {
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new RuntimeException("Http error: " + status + ", body: " + response.body());
        }
        return response;
    }
    public CompletableFuture<String> getWithAuth(String url, String serviceKey) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT))
                .header("Accept", "application/json")
                .header("User-Agent", "TravelInfoApp/1.0 (student project; no website)")
                .header("Authorization", "Bearer " + serviceKey)
                .header("X-Places-Api-Version", "2025-06-17") // версия из документации
                .GET()
                .build();

        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(this::checkStatus)
                .thenApply(HttpResponse::body);
    }

}
