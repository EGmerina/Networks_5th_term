package org.example.async_locator;

import javafx.collections.ObservableList;
import org.example.async_locator.models.Location;
import org.example.async_locator.services.GraphHopperService;

import java.util.ArrayList;

public class AsyncLocationExplorer {
    private final GraphHopperService graphHopperService = new GraphHopperService();

    public AsyncLocationExplorer() {

    }

    public void getLocations(String query, ObservableList<String> locationObservableList) {
        graphHopperService.getLocations(query).thenAccept(locations -> {
            for (Location location : locations) {
                locationObservableList.add(location.toString());
            }
        });
    }
}
