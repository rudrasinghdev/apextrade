package com.rudra.apextrade.gateway.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin(origins = "*")
@RestController
public class HealthController {

    @GetMapping({"/health", "/actuator/health"})
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("ApexTrade Core Online");
    }
}

