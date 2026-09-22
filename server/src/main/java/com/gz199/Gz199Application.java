package com.gz199;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Gz199Application {
    public static void main(String[] args) {
        SpringApplication.run(Gz199Application.class, args);
    }
}
