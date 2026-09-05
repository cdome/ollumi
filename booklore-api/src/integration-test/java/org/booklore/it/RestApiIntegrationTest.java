package org.booklore.it;

import org.booklore.model.entity.BookLoreUserEntity;
import org.booklore.model.entity.UserPermissionsEntity;
import org.booklore.model.enums.ProvisioningMethod;
import org.booklore.it.util.AuthTestHelper;
import org.booklore.it.util.TestDataFactory;
import org.booklore.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.NoOpResponseErrorHandler;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.testcontainers.mariadb.MariaDBContainer;
import tools.jackson.databind.json.JsonMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "app.path-config=build/tmp/it-config",
                "app.bookdrop-folder=build/tmp/it-bookdrop",
                "app.force-disable-oidc=true",
                "app.telemetry.base-url=http://localhost:9/telemetry-disabled",
                "spring.task.scheduling.enabled=false",
                "spring.datasource.hikari.maximum-pool-size=5",
                "spring.datasource.hikari.minimum-idle=1",
                "logging.level.org.booklore=WARN"
        }
)
public abstract class RestApiIntegrationTest {

    static final MariaDBContainer DB = new MariaDBContainer("mariadb:11.4")
            .withDatabaseName("booklore_it")
            .withUsername("booklore")
            .withPassword("booklore");

    static {
        try {
            Files.createDirectories(Path.of("build/tmp/it-config"));
            Files.createDirectories(Path.of("build/tmp/it-bookdrop"));
        } catch (Exception ignored) {
        }
        DB.start();
    }

    @DynamicPropertySource
    static void dbProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", DB::getJdbcUrl);
        registry.add("spring.datasource.username", DB::getUsername);
        registry.add("spring.datasource.password", DB::getPassword);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected TestDataFactory data;

    @Autowired
    protected JsonMapper objectMapper;

    @LocalServerPort
    protected int port;

    protected RestTemplate rest;

    protected AuthTestHelper auth;

    protected static final String ADMIN_USERNAME = "admin";
    protected static final String ADMIN_PASSWORD = "adminpassword123";

    @BeforeEach
    final void initTestHelpers() {
        cleanTestState();
        ensureAdmin();
        rest = new RestTemplate(new JdkClientHttpRequestFactory());
        rest.setErrorHandler(new NoOpResponseErrorHandler());
        List<HttpMessageConverter<?>> converters = new ArrayList<>();
        for (HttpMessageConverter<?> converter : rest.getMessageConverters()) {
            if (converter instanceof JacksonJsonHttpMessageConverter) {
                converters.add(new JacksonJsonHttpMessageConverter(objectMapper));
            } else {
                converters.add(converter);
            }
        }
        rest.setMessageConverters(converters);
        auth = new AuthTestHelper(rest, passwordEncoder, userRepository, jdbc);
    }

    private void cleanTestState() {
        jdbc.update("DELETE FROM refresh_token");
        jdbc.update("DELETE FROM audit_log");
    }

    private void ensureAdmin() {
        if (userRepository.findByUsername(ADMIN_USERNAME).isPresent()) {
            return;
        }
        BookLoreUserEntity user = new BookLoreUserEntity();
        user.setUsername(ADMIN_USERNAME);
        user.setName("Integration Test Admin");
        user.setEmail("admin@example.com");
        user.setProvisioningMethod(ProvisioningMethod.LOCAL);
        user.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        user.setDefaultPassword(false);

        UserPermissionsEntity perms = new UserPermissionsEntity();
        perms.setPermissionAdmin(true);
        perms.setUser(user);
        user.setPermissions(perms);

        userRepository.save(user);
    }

    protected String baseUrl() {
        return "http://localhost:" + port;
    }
}
