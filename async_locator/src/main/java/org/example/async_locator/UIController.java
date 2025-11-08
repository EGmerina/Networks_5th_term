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
import org.example.async_locator.services.GraphHopperService;
import org.example.async_locator.services.OpenWeatherService;
import org.example.async_locator.services.OverpassService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class UIController {
    private final ObservableList<String> locationObservableList = FXCollections.observableArrayList();
    private final GraphHopperService graphHopperService = new GraphHopperService();
    private final OpenWeatherService openWeatherService = new OpenWeatherService();
    private final OverpassService overpassService = new OverpassService();
    private final HashMap<String, Location> receivedLocations = new HashMap<>();

    @FXML
    private TextFlow weatherTextFlow;
    @FXML
    private TextField inputField;
    @FXML
    private ListView<String> locationsListView;
    @FXML
    private Accordion placesList;
    @FXML
    private StackPane stackPane;
    @FXML
    private Button back;

    @FXML
    public void initialize() {
        initListView();
    }

    private void initListView() {
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
        back.setVisible(false);
        weatherTextFlow.setVisible(false);
        placesList.setVisible(false);
        locationsListView.setVisible(true);
    }

    @FXML
    protected void onSearchButtonClick() {
        back.setVisible(false);
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
        placesList.setVisible(false);
        locationsListView.setVisible(true);
        back.setVisible(false);
        weatherTextFlow.setVisible(false);
    }

    private void handleLocationSelect(String newVal) {
        back.setVisible(true);
        locationsListView.setVisible(false);
        placesList.setVisible(true);
        weatherTextFlow.setVisible(true);

        Location location = receivedLocations.get(newVal);

        CompletableFuture<Weather> weatherFuture = openWeatherService.getWeather(location);
        CompletableFuture<ArrayList<Place>> placesFuture = overpassService.getPlaces(location);

        weatherFuture.thenCombine(placesFuture, (weather, places) -> new Object[]{weather, places})
                .thenCompose(result -> {
                    Weather weather = (Weather) result[0];
                    ArrayList<Place> places = (ArrayList<Place>) result[1];

                    // создаём список задач на загрузку описаний
                    var descriptionFutures = places.stream()
                            .map(place -> overpassService.getDescription(place.getTag())
                                    .thenApply(desc -> {
                                        place.setDescription(desc);
                                        return place;
                                    }))
                            .toList();

                    // ждём, пока все описания загрузятся
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
                            TitledPane pane = new TitledPane(
                                    place.getName(),
                                    new Label(place.getDescription() != null ? place.getDescription() : "Описание отсутствует")
                            );
                            placesList.getPanes().add(pane);
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
}
