module org.example.snakeonthenetwork {
    requires javafx.controls;
    requires javafx.fxml;


    opens org.example.snakeonthenetwork to javafx.fxml;
    exports org.example.snakeonthenetwork;
    exports org.example.snakeonthenetwork.ui;
    opens org.example.snakeonthenetwork.ui to javafx.fxml;
}