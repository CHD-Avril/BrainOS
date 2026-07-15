package com.brainos.foundation;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "brainos.admin")
public record AdminSeedProperties(
        @NotBlank
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "BRAINOS_ADMIN_PASSWORD must be provided")
        String password) {}
