package com.example.lsmkvengine;

import java.io.File;

public class Runner {
    public static void main(String[] args) {
        try {
            File dbDir = new File("./data");
            Engine engine = new Engine(dbDir);
            engine.put("ping", "pong");
            System.out.println("Engine started. ping -> " + engine.get("ping"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
