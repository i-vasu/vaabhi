package com.vaabhi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

@Modulithic(sharedModules = "shared")
@SpringBootApplication
public class VaabhiApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaabhiApplication.class, args);
    }
}
