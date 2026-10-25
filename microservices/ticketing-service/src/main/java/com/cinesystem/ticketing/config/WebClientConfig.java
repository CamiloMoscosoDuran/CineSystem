package com.cinesystem.ticketing.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuración del WebClient para comunicación con movie-catalog-service.
 */
@Configuration
public class WebClientConfig {

    /**
     * Bean del WebClient builder con configuración base.
     * La URL base se inyecta en MovieCatalogClient desde application.yml.
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
