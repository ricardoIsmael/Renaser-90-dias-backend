package com.renaser.os.notifications.domain.model.whatsapp;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * El WhatsApp de una persona en formato internacional, listo para el proveedor (spec 041, FR-013): {@code +51987654321}, sin espacios,
 * con {@code +} y de 8 a 15 digitos. El dato vive en un solo lugar, la respuesta {@code whatsapp} de la Ficha Inicial
 * ({@code "+51 987654321"}); aca solo se normaliza para enviar y se enmascara para el registro. Nunca se copia a otra columna.
 */
public final class NumeroDeWhatsApp {

    private static final Pattern INTERNACIONAL = Pattern.compile("^\\+[1-9]\\d{7,14}$");
    private static final char MASCARA = '•';

    private final String valor;

    private NumeroDeWhatsApp(String valor) {
        this.valor = valor;
    }

    /** Normaliza lo que guarda la ficha; vacio si no tiene forma de numero (el servidor no lo valida al guardarlo, asi que puede venir cualquier cosa). */
    public static Optional<NumeroDeWhatsApp> desdeLaFicha(String textoDeLaFicha) {
        if (textoDeLaFicha == null) {
            return Optional.empty();
        }
        String compacto = textoDeLaFicha.replaceAll("[\\s().-]", "");
        return INTERNACIONAL.matcher(compacto).matches() ? Optional.of(new NumeroDeWhatsApp(compacto)) : Optional.empty();
    }

    /** {@code +51987654321}: lo que se manda al proveedor. */
    public String valor() {
        return valor;
    }

    /**
     * {@code +51 9•• ••• •21}: para el registro y para decirle a la persona a que numero se le escribe. Deja ver el codigo de pais, un digito
     * y los dos ultimos; el resto va enmascarado, en grupos de 2, 3 y lo que sobre.
     */
    public String enmascarado() {
        String digitos = valor.substring(1);
        String cabeza = digitos.substring(0, 2) + " " + digitos.charAt(2);
        String cola = digitos.substring(digitos.length() - 2);
        return "+" + cabeza + grupos(digitos.length() - 5) + cola;
    }

    private static String grupos(int ocultos) {
        StringBuilder grupos = new StringBuilder();
        int pendientes = ocultos;
        for (int tamano : new int[] {2, 3, Integer.MAX_VALUE}) {
            int ahora = Math.min(tamano, pendientes);
            if (ahora == 0) {
                break;
            }
            grupos.append(grupos.isEmpty() ? "" : " ").append(String.valueOf(MASCARA).repeat(ahora));
            pendientes -= ahora;
        }
        return grupos.toString();
    }

    @Override
    public String toString() {
        return enmascarado();
    }
}
