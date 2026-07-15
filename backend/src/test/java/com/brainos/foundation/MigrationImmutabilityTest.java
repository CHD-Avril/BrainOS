package com.brainos.foundation;

import static org.assertj.core.api.Assertions.assertThat;

import com.brainos.RepositoryFiles;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

class MigrationImmutabilityTest {

    @Test
    void publishedAdminSeedRemainsImmutableAndPasswordOverrideMovesToV3() throws Exception {
        String publishedV2 = Files.readString(RepositoryFiles.find(
                "backend/src/main/resources/db/migration/V2__seed_admin.sql"));
        String passwordOverrideV3 = Files.readString(RepositoryFiles.find(
                "backend/src/main/resources/db/migration/V3__configure_admin_password.sql"));

        assertThat(publishedV2)
                .contains("$2y$12$iVaclgK5G.7lheVljyIgCu4JplywYn0y4fBIPp4NCQ3lpEDEZoz3O")
                .doesNotContain("${adminPasswordHash}");
        assertThat(passwordOverrideV3)
                .contains("${adminPasswordHash}")
                .contains("UPDATE sys_user");
    }
}
