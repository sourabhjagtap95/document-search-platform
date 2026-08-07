package com.docsearch.api;

import com.docsearch.domain.SortBy;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Binds {@code ?sort=} to {@link SortBy} case-insensitively.
 *
 * <p>Spring's built-in enum converter matches the constant name exactly, so it accepts
 * {@code TITLE} and rejects {@code title} — while every other value in this API, and the
 * endpoint's own documentation, is lower case. Without this the documented spelling is the one
 * that fails, which is the worst way round.
 *
 * <p>An unknown value still raises {@code IllegalArgumentException} here, which Spring wraps as
 * a type mismatch and the handler renders as {@code 400}. Silently defaulting an unrecognised
 * sort would answer a different question than the one asked.
 */
@Component
public class SortByConverter implements Converter<String, SortBy> {

    @Override
    public SortBy convert(String source) {
        return SortBy.valueOf(source.trim().toUpperCase(Locale.ROOT));
    }
}
