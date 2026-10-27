package com.controlfinan.web;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Like Rails' /up: 200 when the app is running. */
@RestController
public class HealthController {
    @GetMapping("/up")
    public ResponseEntity<String> up() {
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML)
            .body("<!DOCTYPE html><html><body style=\"background-color: green\"></body></html>");
    }
}
