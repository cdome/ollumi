package org.booklore.config;

import org.jooq.conf.Settings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JooqConfig {

    /**
     * The datasource is always connected to a single database (the {@code booklore} schema in
     * production/Testcontainers), so qualifying every table with that schema name is redundant.
     * Rendering schema-relative SQL keeps generated queries portable to the H2 database used by
     * the unit-test Spring context, which has no {@code booklore} schema.
     */
    @Bean
    public Settings jooqSettings() {
        return new Settings().withRenderSchema(false);
    }
}
