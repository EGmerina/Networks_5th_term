package org.example.snakeonthenetwork.frontend;

import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.MenuType;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;

import java.io.IOException;

public class MainMenu extends FXGLMenu {
    public MainMenu() {
        super(MenuType.MAIN_MENU);

        try {

            FXMLLoader fxmlLoader = new FXMLLoader(Application.class.getResource("view/main-view.fxml"));
            Parent fxmlRoot = fxmlLoader.load();
            getContentRoot().getChildren().add(fxmlRoot);

        } catch (IOException e) {

            e.printStackTrace();

        }
    }
}
