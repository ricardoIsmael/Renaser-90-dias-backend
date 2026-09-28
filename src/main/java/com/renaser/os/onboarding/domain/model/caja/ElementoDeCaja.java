package com.renaser.os.onboarding.domain.model.caja;

import java.util.regex.Pattern;

/**
 * Un elemento del contenido de la caja (una opción de la pregunta {@code caja_contenido}, V82).
 *
 * @param valor    la clave estable que guarda el checklist ({@code caja_verde}); si el Admin no la da, sale
 *                 de la etiqueta
 * @param etiqueta lo que se lee («Caja verde»)
 */
public record ElementoDeCaja(String valor, String etiqueta) {

    private static final Pattern VALOR = Pattern.compile("[a-z0-9_]{1,40}");

    /** @throws IllegalArgumentException si la etiqueta está vacía o es muy larga, o el valor no sirve (400) */
    public static ElementoDeCaja de(String valor, String etiqueta) {
        String limpia = TextoCorto.obligatorio(etiqueta, 80, "El nombre del elemento");
        String clave = valor == null || valor.isBlank() ? claveDe(limpia) : valor.strip();
        if (!VALOR.matcher(clave).matches()) {
            throw new IllegalArgumentException("La clave «" + clave + "» solo puede tener minúsculas, números y _.");
        }
        return new ElementoDeCaja(clave, limpia);
    }

    private static String claveDe(String etiqueta) {
        String clave = TextoCorto.normalizado(etiqueta).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return clave.length() > 40 ? clave.substring(0, 40) : clave;
    }
}
