package org.booklore.test;

import org.booklore.service.migration.AppMigrationStartup;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
public abstract class AbstractIntegrationTest {

    static final MariaDBContainer<?> MARIADB = new MariaDBContainer<>("mariadb:11.4")
            .withDatabaseName("booklore")
            .withUsername("booklore")
            .withPassword("booklore");

    static {
        MARIADB.start();
    }

    @MockitoBean
    @SuppressWarnings("unused")
    private AppMigrationStartup appMigrationStartup;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MARIADB::getJdbcUrl);
        registry.add("spring.datasource.username", MARIADB::getUsername);
        registry.add("spring.datasource.password", MARIADB::getPassword);
        registry.add("spring.flyway.enabled", () -> true);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "none");
        registry.add("app.path-config", () -> "build/app-data");
        registry.add("app.bookdrop-folder", () -> "build/bookdrop");
    }
}
