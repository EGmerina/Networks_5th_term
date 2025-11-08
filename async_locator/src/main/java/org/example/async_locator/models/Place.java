package org.example.async_locator.models;

public class Place {
    private String tag;
    private String name;
    private String description;

    public Place(String tag, String name, String description){
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
}
