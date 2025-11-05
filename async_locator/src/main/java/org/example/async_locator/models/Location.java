package org.example.async_locator.models;

import java.util.Objects;

public record Location(String name, double lat, double lon) {
    @Override
    public String toString() {
        return name + " ( " + lat + ", " + lon + " )";
    }

    @Override
    public boolean equals(Object obj) {
        Location location = (Location) obj;
        if (this == location) {
            return true;
        }
        if (this.name().equals(location.name()) && this.lat() == location.lat() && this.lon() == location.lon()) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, lat, lon);
    }
}
