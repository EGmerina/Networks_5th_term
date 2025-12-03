module org.example.snakeonthenetwork {
    requires javafx.controls;
    requires javafx.fxml;
    requires protobuf.java;
    requires com.almasb.fxgl.all;
    requires javafx.graphics;

    opens org.example.snakeonthenetwork to javafx.fxml;
    exports org.example.snakeonthenetwork;
    exports org.example.snakeonthenetwork.frontend;
    opens org.example.snakeonthenetwork.frontend to javafx.fxml;


}