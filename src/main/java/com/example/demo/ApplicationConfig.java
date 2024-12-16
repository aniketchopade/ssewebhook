package com.example.demo;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.jsonb.JsonBindingFeature;

public class ApplicationConfig extends ResourceConfig {
    public ApplicationConfig() {
        // Register the JSON-B feature
        register(JsonBindingFeature.class);
        // Register other necessary components
        packages("com.example.demo");
    }
}