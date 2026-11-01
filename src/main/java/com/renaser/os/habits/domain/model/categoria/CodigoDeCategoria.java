package com.renaser.os.habits.domain.model.categoria;

import java.util.Locale;
import java.util.Map;

/**
 * Como se dice una categoria hacia afuera (spec 023). La API de hoy expone las cuatro de la Escuela con un codigo en ingles ({@code BODY},
 * {@code MIND}, {@code CONSCIENCE}, {@code SPIRIT}) que la app publicada ya entiende; cualquier otra categoria viaja con su clave tal cual
 * ({@code DIPLOMADO_ESTUDIO}). Una app anterior que no conoce una clave la muestra con la etiqueta generica, sin romperse (FR-013).
 */
public final class CodigoDeCategoria {

    private static final Map<String, String> CODIGO_POR_CLAVE = Map.of(
            "CUERPO", "BODY", "MENTE", "MIND", "CONSCIENCIA", "CONSCIENCE", "ESPIRITU", "SPIRIT");
    private static final Map<String, String> CLAVE_POR_CODIGO = Map.of(
            "BODY", "CUERPO", "MIND", "MENTE", "CONSCIENCE", "CONSCIENCIA", "SPIRIT", "ESPIRITU");

    private CodigoDeCategoria() {
    }

    /** El codigo con el que sale la categoria en la API: el ingles de las cuatro de siempre o la clave. */
    public static String deClave(String clave) {
        return CODIGO_POR_CLAVE.getOrDefault(clave, clave);
    }

    /** La clave de la base a partir de lo que mande la app: el codigo en ingles de las cuatro o la clave misma (sin distinguir mayusculas). */
    public static String aClave(String codigoOClave) {
        if (codigoOClave == null || codigoOClave.isBlank()) {
            throw new IllegalArgumentException("La categoría es obligatoria.");
        }
        String limpio = codigoOClave.trim().toUpperCase(Locale.ROOT);
        return CLAVE_POR_CODIGO.getOrDefault(limpio, limpio);
    }
}
