package com.backendapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ComponentScan(basePackages = {"com.backendapi", "loader"})
@EnableScheduling // para ActualizadorTiempoReal
public class BackendApiApplication {
    public static void main(String[] args) {
        SpringApplication.run(BackendApiApplication.class, args);
    }
}
