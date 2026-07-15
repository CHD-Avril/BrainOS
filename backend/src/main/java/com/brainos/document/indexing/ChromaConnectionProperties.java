package com.brainos.document.indexing;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "brainos.chroma")
public record ChromaConnectionProperties(
        @NotBlank
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "CHROMA_URL must be provided")
        String baseUrl,
        @NotBlank
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "CHROMA_TENANT must be provided")
        String tenant,
        @NotBlank
        @Pattern(regexp = "^(?!\\$\\{).+$", message = "CHROMA_DATABASE must be provided")
        String database,
        @Pattern(regexp = "^(?!\\$\\{).*$", message = "CHROMA_API_KEY must be provided")
        String apiKey) {

    @AssertTrue(message = "CHROMA_API_KEY must be provided when using Chroma Cloud")
    public boolean isCloudAuthenticationConfigured() {
        return !isCloudUrl(baseUrl) || (apiKey != null && !apiKey.isBlank());
    }

    private static boolean isCloudUrl(String value) {
        if (value == null) {
            return false;
        }
        String normalized = value.toLowerCase();
        return normalized.contains("api.trychroma.com")
                || normalized.contains("api-aws.trychroma.com");
    }
}
