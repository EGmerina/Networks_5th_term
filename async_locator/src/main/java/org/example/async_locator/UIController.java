package org.example.async_locator;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.example.async_locator.models.Location;
import org.example.async_locator.models.Place;
import org.example.async_locator.models.Weather;
import org.example.async_locator.services.FoursquareService;
import org.example.async_locator.services.GraphHopperService;
import org.example.async_locator.services.OpenWeatherService;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UIController {
    private final ObservableList<String> locationObservableList = FXCollections.observableArrayList();
    private final GraphHopperService graphHopperService = new GraphHopperService();
    private final OpenWeatherService openWeatherService = new OpenWeatherService();
    private final FoursquareService foursquareService = new FoursquareService();
    private final HashMap<String, Location> receivedLocations = new HashMap<>();
    private String lastSelected = null;

    @FXML
    private Label infoLabel;
    @FXML
    private TextFlow weatherTextFlow;
    @FXML
    private ScrollPane scrollPane;
    @FXML
    private TextField inputField;
    @FXML
    private ListView<String> locationsListView;
    @FXML
    private Accordion placesList;
    @FXML
    private Button back;

    @FXML
    public void initialize() {
        initListView();
        scrollPane.setFitToWidth(true);
    }

    private void initListView() {
        goToLocationsList();
        locationsListView.setItems(locationObservableList);
        locationsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {

                handleLocationSelect(newVal);
            }
        });

    }

    @FXML
    protected void onClearButtonClick() {
        locationObservableList.clear();
        receivedLocations.clear();
        inputField.clear();
        placesList.getPanes().clear();
        goToLocationsList();
    }

    @FXML
    protected void onSearchButtonClick() {
        goToLocationsList();
        locationObservableList.clear();
        receivedLocations.clear();
        String query = inputField.getText();
        if (query.equals("")) {
            return;
        }
        graphHopperService.getLocations(query).thenAccept(locations -> {
            Platform.runLater(() -> {
                for (Location location : locations) {
                    locationObservableList.add(location.toString());
                    receivedLocations.put(location.toString(), location);
                }
            });
        });
    }

    @FXML
    protected void onBackButtonClick() {
        goToLocationsList();
        locationsListView.getSelectionModel().clearSelection();
    }

    private void handleLocationSelect(String newVal) {
        goToPlacesList();
        infoLabel.setText("Loading...");
        Location location = receivedLocations.get(newVal);

        CompletableFuture<Weather> weatherFuture = openWeatherService.getWeather(location);
        CompletableFuture<ArrayList<Place>> placesFuture = foursquareService.getPlaces(location);

        weatherFuture.thenCombine(placesFuture, (weather, places) -> new Object[]{weather, places})
                .thenCompose(result -> {
                    Weather weather = (Weather) result[0];
                    ArrayList<Place> places = (ArrayList<Place>) result[1];


                    var descriptionFutures = places.stream()
                            .map(place -> foursquareService.getDescription(place.getTag())
                                    .thenApply(desc -> {
                                        place.setDescription(desc);
                                        return place;
                                    }))
                            .toList();


                    return CompletableFuture.allOf(descriptionFutures.toArray(new CompletableFuture[0]))
                            .thenApply(v -> new Object[]{weather,
                                    descriptionFutures.stream().map(CompletableFuture::join).toList()});
                })

                .thenAccept(result -> {
                    Weather weather = (Weather) result[0];
                    @SuppressWarnings("unchecked")
                    var places = (List<Place>) result[1];

                    Platform.runLater(() -> {
                        weatherTextFlow.getChildren().clear();
                        weatherTextFlow.getChildren().add(new Text(weather.toString()));

                        placesList.getPanes().clear();
                        for (Place place : places) {
                            if (place.getDescription() == null) {
                                continue;
                            }
                            TitledPane pane = new TitledPane(
                                    place.getName(),
                                    new Label(place.getDescription()));
                            placesList.getPanes().add(pane);
                        }
                        if (placesList.getPanes().isEmpty()) {
                            infoLabel.setText("Empty");
                        } else {
                            infoLabel.setText("");
                        }
                    });
                })

                .exceptionally(ex -> {
                    ex.printStackTrace();
                    Platform.runLater(() -> {
                        Alert alert = new Alert(Alert.AlertType.ERROR, "Ошибка: " + ex.getMessage(), ButtonType.OK);
                        alert.showAndWait();
                    });
                    return null;
                });
    }

    private void goToLocationsList() {
        back.setVisible(false);
        weatherTextFlow.setVisible(false);
        scrollPane.setVisible(false);
        locationsListView.setVisible(true);
        infoLabel.setText("");
        placesList.getPanes().clear();
    }

    private void goToPlacesList() {
        back.setVisible(true);
        locationsListView.setVisible(false);
        scrollPane.setVisible(true);
        weatherTextFlow.setVisible(true);

    }
}
