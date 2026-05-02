package com.invoicebuilder.tenant;

import org.springframework.stereotype.Component;

import java.text.Normalizer;

/**
 * Generates unique tenant slugs from human-readable names.
 *
 * <p>Strips diacritics, lowercases, replaces non-alphanumeric runs with a single
 * dash, and appends a numeric suffix until the slug is free in the repository.</p>
 */
@Component
public class TenantSlugGenerator {

    private static final int MAX_LENGTH = 80;
    private static final int MAX_SUFFIX_TRIES = 1000;

    private final TenantRepository tenantRepository;

    public TenantSlugGenerator(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    public String generate(String name) {
        String base = slugify(name);
        if (base.isEmpty()) {
            base = "tenant";
        }
        if (!tenantRepository.existsBySlug(base)) {
            return base;
        }
        for (int i = 2; i <= MAX_SUFFIX_TRIES; i++) {
            String candidate = base + "-" + i;
            if (!tenantRepository.existsBySlug(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Unable to generate unique tenant slug for name: " + name);
    }

    static String slugify(String input) {
        if (input == null) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = normalized.toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.length() > MAX_LENGTH ? slug.substring(0, MAX_LENGTH) : slug;
    }
}
