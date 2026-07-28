package com.recruitment.config;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class StartupBanner {

    private final Environment environment;

    public StartupBanner(Environment environment) {
        this.environment = environment;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        String port = environment.getProperty("server.port", "8080");
        String profile = String.join(", ", environment.getActiveProfiles());
        if (profile.isBlank()) {
            profile = "default";
        }
        System.out.printf("%nTuniHire started on http://localhost:%s  [%s]%n%n", port, profile);
    }
}
