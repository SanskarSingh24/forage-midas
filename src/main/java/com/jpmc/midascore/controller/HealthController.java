package com.jpmc.midascore.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping("/health")
    public String health() {
        return "Application is running on Java 21!";
    }

    @GetMapping("/")
    public String home() {
        return "Welcome to Midas Core - Running on Java 21 LTS";
    }
}
