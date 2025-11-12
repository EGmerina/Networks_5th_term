package org.example.async_locator.models;

import java.util.Objects;

public class Place {
    private String tag;
    private String name;
    private String description;

    public Place(String tag, String name, String description) {
        this.tag = tag;
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getTag() {
        return tag;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String desc) {
        description = desc;
    }

    @Override
    public boolean equals(Object obj) {
        org.example.async_locator.models.Place place = (Place) obj;
        if (this == place) {
            return true;
        }
        if (this.name.equals(place.getName()) && this.tag.equals(place.getTag()) && this.description.equals(place.getDescription())) {
            return true;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(tag, name, description);

    }
}