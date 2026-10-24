package org.booklore.config.security.filter;

import org.booklore.config.security.JwtUtils;
import org.booklore.mapper.custom.BookLoreUserTransformer;
import org.booklore.repository.jooq.JooqUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class CustomFontJwtFilter extends AbstractQueryParameterJwtFilter {

    public CustomFontJwtFilter(
            JwtUtils jwtUtils,
            JooqUserRepository userRepository,
            BookLoreUserTransformer bookLoreUserTransformer) {
        super(jwtUtils, userRepository, bookLoreUserTransformer);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.startsWith("/api/v1/custom-fonts/") && path.endsWith("/file"));
    }
}
