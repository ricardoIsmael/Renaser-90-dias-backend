package com.renaser.os.leadership.domain.model.documento;

import java.util.Locale;
import java.util.Map;

/**
 * Cómo se llama un pilar en el documento: el mismo nombre con que la ficha del mentor agrupa los hábitos (spec 055, FR-013). La clave
 * {@code CONSCIENCIA} es el pilar «Emociones». Un pilar de otra formación se dice con su clave en tipo oración, y un hábito sin pilar,
 * «Otros»: nunca se descarta un hábito por no saber en qué pilar va.
 */
public final class NombreDelPilar {

    public static final String SIN_PILAR = "Otros";

    private static final Map<String, String> CONOCIDOS = Map.of(
            "CUERPO", "Cuerpo",
            "MENTE", "Mente",
            "ESPIRITU", "Espíritu",
            "CONSCIENCIA", "Emociones");

    private NombreDelPilar() {
    }

    public static String de(String clave) {
        if (clave == null || clave.isBlank()) {
            return SIN_PILAR;
        }
        String limpia = clave.trim().toUpperCase(Locale.ROOT);
        String conocido = CONOCIDOS.get(limpia);
        if (conocido != null) {
            return conocido;
        }
        String minuscula = limpia.toLowerCase(Locale.ROOT).replace('_', ' ');
        return minuscula.substring(0, 1).toUpperCase(Locale.ROOT) + minuscula.substring(1);
    }
}
