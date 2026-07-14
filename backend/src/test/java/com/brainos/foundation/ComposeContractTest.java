package com.brainos.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.RepositoryFiles;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

class ComposeContractTest {

    @Test
    @SuppressWarnings("unchecked")
    void composePinsHealthyPersistentDataServices() throws Exception {
        Map<String, Object> compose =
                new Yaml().load(Files.readString(RepositoryFiles.find("docker-compose.yml")));
        Map<String, Map<String, Object>> services =
                (Map<String, Map<String, Object>>) compose.get("services");

        assertThat(services).containsOnlyKeys("mysql", "redis", "chroma");
        assertThat(services.get("mysql")).containsEntry("image", "mysql:8.4");
        assertThat(services.get("redis")).containsEntry("image", "redis:7.4-alpine");
        assertThat(services.get("chroma"))
                .containsEntry("image", "ghcr.io/chroma-core/chroma:1.0.0");

        assertThat(services.values())
                .allSatisfy(service -> assertThat(service).containsKey("healthcheck"));
        Map<String, Object> chromaHealth =
                (Map<String, Object>) services.get("chroma").get("healthcheck");
        assertThat(String.join(" ", (List<String>) chromaHealth.get("test")))
                .contains("/api/v2/heartbeat");

        Map<String, Object> volumes = (Map<String, Object>) compose.get("volumes");
        assertThat(volumes).containsOnlyKeys("mysql_data", "redis_data", "chroma_data");
        assertThat((List<String>) services.get("mysql").get("volumes"))
                .contains("mysql_data:/var/lib/mysql");
        assertThat((List<String>) services.get("redis").get("volumes"))
                .contains("redis_data:/data");
        assertThat((List<String>) services.get("chroma").get("volumes"))
                .contains("chroma_data:/data");
    }
}
