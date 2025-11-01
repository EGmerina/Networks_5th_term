package org.example.async_locator;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public class UIController {
    @FXML
    private Button search, clear;
    @FXML
    private TextField inputField;
    @FXML
    private ListView<String> listView;

    @FXML
    protected void onClearButtonClick() {

    }

    @FXML
    protected void onSearchButtonClick() {
        String input = inputField.getText();

    }
}
