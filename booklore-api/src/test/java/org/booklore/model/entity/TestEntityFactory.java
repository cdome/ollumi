package org.booklore.model.entity;

import java.util.concurrent.atomic.AtomicLong;

public class TestEntityFactory {

    private static final AtomicLong idCounter = new AtomicLong(1);

    public static AuthorEntity createAuthor(String name) {
        return AuthorEntity.builder()
                .id(idCounter.getAndIncrement())
                .name(name)
                .build();
    }

}