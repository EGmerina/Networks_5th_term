package org.example.async_locator;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.StackPane;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.example.async_locator.models.Location;
import org.example.async_locator.services.GraphHopperService;
import org.example.async_locator.services.OpenWeatherService;

import java.util.ArrayList;
import java.util.HashMap;

public class UIController {
    private final ObservableList<String> locationObservableList = FXCollections.observableArrayList();
    private final GraphHopperService graphHopperService = new GraphHopperService();
    private final OpenWeatherService openWeatherService = new OpenWeatherService();
    private final HashMap<String, Location> receivedLocations = new HashMap<>();

    @FXML
    private TextFlow weatherTextFlow;
    @FXML
    private TextField inputField;
    @FXML
    private ListView<String> locationsListView, placesListView;
    @FXML
    private StackPane stackPane;
    @FXML
    private TextArea descTextArea;

    @FXML
    public void initialize() {
        initListView();
    }

    private void initListView() {
        locationsListView.setItems(locationObservableList);
        locationsListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                locationsListView.setVisible(false);
                placesListView.setVisible(true);
                weatherTextFlow.setVisible(true);
                handleLocationSelect(newVal);
            }
        });
    }

    @FXML
    protected void onClearButtonClick() {
        locationObservableList.clear();
        receivedLocations.clear();
        inputField.clear();
        weatherTextFlow.setVisible(false);
        placesListView.setVisible(false);
        descTextArea.setVisible(false);
        locationsListView.setVisible(true);
    }

    @FXML
    protected void onSearchButtonClick() {
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

    private void handleLocationSelect(String newVal) {

        openWeatherService.getWeather(receivedLocations.get(newVal)).thenAccept(weather -> {
            Platform.runLater(() -> {
                Text text = new Text(weather.toString());
                weatherTextFlow.getChildren().add(text);
            });
        });
    }
}
