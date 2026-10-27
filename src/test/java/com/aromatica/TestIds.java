package com.aromatica;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** id для тестов: имя читается в тесте («test-aroma-1»), а в модели id — UUID, стабильный для имени. */
public final class TestIds {

    private TestIds() {
    }

    public static UUID id(String name) {
        return UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
    }
}
