package com.ecommerce.api.catalog;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

final class Slugs {

    private static final Pattern ACCENTS = Pattern.compile("\\p{M}");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern EDGE_HYPHENS = Pattern.compile("^-|-$");

    private Slugs() {
    }

    static String from(String text) {
        String withoutAccents = ACCENTS.matcher(Normalizer.normalize(text, Normalizer.Form.NFD)).replaceAll("");
        String hyphenated = NON_ALPHANUMERIC.matcher(withoutAccents.toLowerCase(Locale.ROOT)).replaceAll("-");
        return EDGE_HYPHENS.matcher(hyphenated).replaceAll("");
    }
}
