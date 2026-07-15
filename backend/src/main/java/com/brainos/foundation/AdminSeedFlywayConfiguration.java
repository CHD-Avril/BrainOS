package com.brainos.foundation;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.autoconfigure.flyway.FlywayConfigurationCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({
    AdminSeedProperties.class,
    RequiredDataSourceProperties.class,
    RequiredRedisProperties.class,
    RequiredStorageProperties.class
})
class AdminSeedFlywayConfiguration {

    @Bean
    FlywayConfigurationCustomizer adminSeedPasswordCustomizer(AdminSeedProperties properties) {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);
        return configuration -> {
            Map<String, String> placeholders = new HashMap<>(configuration.getPlaceholders());
            placeholders.put("adminPasswordHash", encoder.encode(properties.password()));
            configuration.placeholders(placeholders);
        };
    }
}
