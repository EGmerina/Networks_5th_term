package org.example.async_locator.models;

public record Weather(double temp, String description) {
    @Override
    public String toString() {
        return "temperature : " + temp + " K, weather : " + description;
    }
}
