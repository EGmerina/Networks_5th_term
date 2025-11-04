package org.example.async_locator;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import org.example.async_locator.models.Location;
import org.example.async_locator.services.GraphHopperService;

import java.util.ArrayList;

public class UIController {
    private final ObservableList<String> locationObservableList = FXCollections.observableArrayList();
    private final AsyncLocationExplorer asyncLocationExplorer = new AsyncLocationExplorer();

    @FXML
    private Button search, clear;
    @FXML
    private TextField inputField;
    @FXML
    private ListView<String> listView;

    @FXML
    public void initialize() {
        initListView();
    }

    private void initListView() {
        listView.setItems(locationObservableList);
    }

    @FXML
    protected void onClearButtonClick() {
        locationObservableList.clear();
    }

    @FXML
    protected void onSearchButtonClick() {
        locationObservableList.clear();
        String query = inputField.getText();
        if (query.equals("")) {
            return;
        }
        asyncLocationExplorer.getLocations(query, locationObservableList);
    }
}
