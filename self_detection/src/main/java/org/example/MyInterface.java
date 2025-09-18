package org.example;

import java.util.ArrayList;
import java.util.HashMap;

public class MyInterface {

    public void show(HashMap<String, Long> copies, Object mutex) {
        ArrayList<String> snapshot;

        while (true) {

            synchronized (mutex) {
                snapshot = new ArrayList<>(copies.keySet());
            }
            System.out.print("\033[2J\033[H");
            System.out.flush();
            for (String key : snapshot) {
                System.out.println(key + " IS ALIVE!");
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
