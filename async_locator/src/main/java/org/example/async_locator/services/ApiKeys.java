package org.example.async_locator.services;

import io.github.cdimascio.dotenv.Dotenv;

public class ApiKeys {
    private static final Dotenv dotenv = Dotenv.load();
    public static final String GRAPHHOPPER_KEY = dotenv.get("GRAPHHOPPER_KEY");
    public static final String OPENWEATHER_KEY = dotenv.get("OPENWEATHER_KEY");
    public static final String FORSQUARE_KEY = dotenv.get("FORSQUARE_KEY");

    static {
        if (GRAPHHOPPER_KEY == null || OPENWEATHER_KEY == null || FORSQUARE_KEY == null) {
            throw new IllegalStateException("API keys not found in environment variables");
        }
    }
}
