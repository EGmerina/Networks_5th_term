module org.example.async_locator {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.net.http;
    requires org.apache.logging.log4j.core;
    requires com.github.benmanes.caffeine;
    requires com.google.gson;
    requires javafx.base;
    requires org.apache.logging.log4j;
    requires javafx.graphics;
    requires io.github.cdimascio.dotenv.java;
    requires org.example.async_locator;


    opens org.example.async_locator to javafx.fxml;
    exports org.example.async_locator;
}