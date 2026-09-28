package com.renaser.os.onboarding.domain.model.caja;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lo que la Ficha Inicial no pregunta y el envío necesita (spec §4): las cinco preguntas opcionales de la
 * sección {@code destino} del flujo {@code caja_renaser}. Todas pueden quedar vacías ({@code null}).
 */
public record DestinoAlternativo(String otraDireccion, String otroCelular, String quienRecibe, String referencias,
                                 String provincia) {

    public static final String CLAVE_OTRA_DIRECCION = "caja_otra_direccion";
    public static final String CLAVE_OTRO_CELULAR = "caja_otro_celular";
    public static final String CLAVE_QUIEN_RECIBE = "caja_quien_recibe";
    public static final String CLAVE_REFERENCIAS = "caja_referencias";
    public static final String CLAVE_PROVINCIA = "caja_provincia";

    public static final DestinoAlternativo VACIO = new DestinoAlternativo(null, null, null, null, null);

    /** Limpia lo que escribió la persona. @throws IllegalArgumentException si algo pasa del largo (400) */
    public static DestinoAlternativo de(String otraDireccion, String otroCelular, String quienRecibe,
                                        String referencias, String provincia) {
        return new DestinoAlternativo(TextoCorto.opcional(otraDireccion, 300, "La otra dirección"),
                TextoCorto.opcional(otroCelular, 30, "El otro número"),
                TextoCorto.opcional(quienRecibe, 120, "Quién la recibe"),
                TextoCorto.opcional(referencias, 300, "Las referencias"),
                TextoCorto.opcional(provincia, 80, "La provincia"));
    }

    /** Desde las respuestas guardadas, por clave de pregunta. */
    public static DestinoAlternativo desde(Map<String, String> porClave) {
        return new DestinoAlternativo(porClave.get(CLAVE_OTRA_DIRECCION), porClave.get(CLAVE_OTRO_CELULAR),
                porClave.get(CLAVE_QUIEN_RECIBE), porClave.get(CLAVE_REFERENCIAS), porClave.get(CLAVE_PROVINCIA));
    }

    /** Cada respuesta con su clave de pregunta; {@code null} = sin respuesta. */
    public Map<String, String> porClave() {
        Map<String, String> porClave = new LinkedHashMap<>();
        porClave.put(CLAVE_OTRA_DIRECCION, otraDireccion);
        porClave.put(CLAVE_OTRO_CELULAR, otroCelular);
        porClave.put(CLAVE_QUIEN_RECIBE, quienRecibe);
        porClave.put(CLAVE_REFERENCIAS, referencias);
        porClave.put(CLAVE_PROVINCIA, provincia);
        return porClave;
    }
}
