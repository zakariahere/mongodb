package com.elzakaria.mongodb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MongodbApplication {

    // NOTE: classic `public static void main`. The Initializr generated the new
    // Java 25 instance main (`void main`), but spring-boot-devtools' Restarter
    // can't locate a non-static main to restart -> "Unable to find the main
    // class to restart" and the app dies before booting. Static main fixes it.
    public static void main(String[] args) {
        SpringApplication.run(MongodbApplication.class, args);
    }

}
