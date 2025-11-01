module org.example.async_locator {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.async_locator to javafx.fxml;
    exports org.example.async_locator;
}