package com.brainos.foundation;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "spring.datasource")
record RequiredDataSourceProperties(
        @NotBlank
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "MYSQL_URL must be provided")
        String url,
        @NotBlank
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "MYSQL_USER must be provided")
        String username,
        @NotBlank
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "MYSQL_PASSWORD must be provided")
        String password) {}

@Validated
@ConfigurationProperties(prefix = "spring.data.redis")
record RequiredRedisProperties(
        @NotBlank
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "REDIS_HOST must be provided")
        String host,
        @NotNull @Min(1) Integer port,
        @Pattern(regexp = "^(?!\\$\\{).*$", message = "REDIS_PASSWORD must be provided")
        String password) {}

@Validated
@ConfigurationProperties(prefix = "brainos.storage")
record RequiredStorageProperties(
        @NotBlank
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "BRAINOS_STORAGE_PATH must be provided")
        String root) {}
