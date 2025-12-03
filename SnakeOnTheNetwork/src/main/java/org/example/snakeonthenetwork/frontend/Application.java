package org.example.snakeonthenetwork.frontend;

import com.almasb.fxgl.app.GameApplication;
import com.almasb.fxgl.app.GameSettings;
import com.almasb.fxgl.app.scene.FXGLMenu;
import com.almasb.fxgl.app.scene.SceneFactory;

public class Application extends GameApplication {

    public static final int BLOCK_SIZE = 32;
    public static int MAP_SIZE;
    private static final int UI_SIZE = 80;


    @Override
    protected void initSettings(GameSettings settings) {
        settings.setWidth(MAP_SIZE * BLOCK_SIZE + UI_SIZE);
        settings.setHeight(MAP_SIZE * BLOCK_SIZE);
        settings.setTitle("Snake on the network");
        settings.setVersion("1.0");
        settings.getCSSList().add("style.css");
        settings.setMainMenuEnabled(true);
        settings.setSceneFactory(new SceneFactory() {
            @Override
            public FXGLMenu newMainMenu() {
                return new MainMenu();
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
    }
}
