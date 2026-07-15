package com.brainos.auth.token;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "brainos.auth.jwt")
public record JwtProperties(
        @NotBlank
        @Size(min = 32, message = "BRAINOS_JWT_SECRET must contain at least 32 characters")
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "BRAINOS_JWT_SECRET must be provided")
        String secret) {}
