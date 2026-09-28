package com.renaser.os.onboarding.domain.model.caja;

import java.util.Map;
import java.util.Set;

/**
 * Los datos de envío de un aprendiz: los que ya dio en la Ficha Inicial (no se copian: se leen de ahí) más
 * su {@link DestinoAlternativo}. Cualquiera puede faltar.
 */
public record FichaDeEnvio(String nombre, String celular, String pais, String ciudad, String distrito,
                           String direccion, String dni, DestinoAlternativo destino) {

    public static final String CLAVE_NOMBRE = "full_name";
    public static final String CLAVE_CELULAR = "whatsapp";
    public static final String CLAVE_PAIS = "country";
    public static final String CLAVE_CIUDAD = "city";
    public static final String CLAVE_DISTRITO = "district";
    public static final String CLAVE_DIRECCION = "address_reference";
    public static final String CLAVE_DNI = "identity_document";

    /** Las claves de pregunta que se leen para armar la ficha (las de la Ficha Inicial y las del destino). */
    public static final Set<String> CLAVES = Set.of(CLAVE_NOMBRE, CLAVE_CELULAR, CLAVE_PAIS, CLAVE_CIUDAD,
            CLAVE_DISTRITO, CLAVE_DIRECCION, CLAVE_DNI, DestinoAlternativo.CLAVE_OTRA_DIRECCION,
            DestinoAlternativo.CLAVE_OTRO_CELULAR, DestinoAlternativo.CLAVE_QUIEN_RECIBE,
            DestinoAlternativo.CLAVE_REFERENCIAS, DestinoAlternativo.CLAVE_PROVINCIA);

    private static final Set<String> PERU = Set.of("peru", "pe", "per", "republica del peru");

    public FichaDeEnvio {
        destino = destino == null ? DestinoAlternativo.VACIO : destino;
    }

    public static FichaDeEnvio desde(Map<String, String> porClave) {
        return new FichaDeEnvio(porClave.get(CLAVE_NOMBRE), porClave.get(CLAVE_CELULAR), porClave.get(CLAVE_PAIS),
                porClave.get(CLAVE_CIUDAD), porClave.get(CLAVE_DISTRITO), porClave.get(CLAVE_DIRECCION),
                porClave.get(CLAVE_DNI), DestinoAlternativo.desde(porClave));
    }

    /**
     * Si la caja sale por la app: país Perú, escrito como sea. <b>Sin país en la ficha se asume Perú</b>
     * (supuesto de D-219: todo el padrón de hoy es de Perú, y dejar a alguien fuera por un campo vacío lo
     * sacaría de la lista sin que nadie lo note).
     */
    public boolean esDelPeru() {
        String normalizado = TextoCorto.normalizado(pais).replaceAll("[^a-z ]", "").strip();
        return normalizado.isEmpty() || PERU.contains(normalizado) || normalizado.contains("peru");
    }
}
