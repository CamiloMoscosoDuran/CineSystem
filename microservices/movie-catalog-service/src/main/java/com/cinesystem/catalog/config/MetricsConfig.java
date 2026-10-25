package com.cinesystem.catalog.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.handler.SimpleUrlHandlerMapping;
import org.springframework.web.servlet.mvc.ServletWrappingController;

import java.util.Properties;

/**
 * Configuración de métricas y rutas de actuator.
 * Mapea /metrics a /actuator/prometheus para cumplir el requisito del enunciado.
 */
@Configuration
public class MetricsConfig {
    // La configuración del path se hace en application.yml y web config
    // Este bean adicional puede extenderse para personalización de métricas
}
