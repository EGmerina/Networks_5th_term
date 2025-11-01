package org.example.async_locator.models;

public record Location(String name, double lat, double lon) {
    @Override
    public String toString() {
        return name + " ( " + lat + ", " + lon + " )";
    }
}
