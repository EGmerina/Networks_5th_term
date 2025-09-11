package org.example;
import java.util.HashMap;

public class MyInterface {

    public void show(HashMap<String, Long> copies) {
        while (true) {
            for (String key : copies.keySet()) {
                System.out.println(key + " IS ALIVE!");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
