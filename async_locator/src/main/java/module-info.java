module org.example.async_locator {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires org.apache.logging.log4j.core;
    requires com.github.benmanes.caffeine;
    requires com.google.gson;
    requires javafx.base;


    opens org.example.async_locator to javafx.fxml;
    exports org.example.async_locator;
}