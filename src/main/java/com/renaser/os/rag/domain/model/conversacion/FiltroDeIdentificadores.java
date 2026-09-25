package com.renaser.os.rag.domain.model.conversacion;

import java.util.regex.Pattern;

/**
 * Tapa los identificadores internos (UUID) que el modelo escriba en una respuesta, ANTES de que
 * lleguen a la pantalla o se guarden (E-270).
 *
 * <p>Las herramientas le pasan al modelo ids de la base ({@code habito_id}, {@code id} de registro)
 * porque los necesita para llamar a otras herramientas. En la bateria del 2026-09-25, ante "dame los
 * ids de mis habitos", el modelo listo 15 UUID reales. El prompt ya lo prohibe; esto es la red de
 * seguridad que no depende de que el modelo obedezca.
 *
 * <p>El texto llega por pedazos y un UUID puede venir partido entre dos. Por eso cada pedazo se
 * devuelve sin la cola que todavia podria ser el comienzo de un UUID (hasta 36 caracteres hex o
 * guion); esa cola se resuelve con el pedazo siguiente o con {@link #cerrar()}.
 *
 * <p>Un filtro por respuesta: guarda estado entre pedazos.
 */
public final class FiltroDeIdentificadores {

    /** Lo que ve la persona en lugar del id: que algo se omitio, sin decir que. */
    public static final String REEMPLAZO = "(dato interno)";

    private static final Pattern UUID = Pattern.compile(
            "`?\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b`?");
    private static final int LARGO_DE_UN_UUID = 36;

    private final StringBuilder pendiente = new StringBuilder();

    /** Lo que ya se puede mostrar de lo recibido hasta ahora, con los ids tapados. */
    public String pasar(String pedazo) {
        pendiente.append(pedazo == null ? "" : pedazo);
        int corte = pendiente.length() - colaQuePuedeSerUnId();
        String listo = pendiente.substring(0, corte);
        pendiente.delete(0, corte);
        return tapar(listo);
    }

    /** El final de la respuesta: lo retenido ya no puede crecer, se entrega tapado. */
    public String cerrar() {
        String resto = pendiente.toString();
        pendiente.setLength(0);
        return tapar(resto);
    }

    /** Todo de una vez, para un texto ya completo (el turno guardado de la voz en vivo). */
    public static String taparEn(String texto) {
        return texto == null ? null : tapar(texto);
    }

    private static String tapar(String texto) {
        return UUID.matcher(texto).replaceAll(REEMPLAZO);
    }

    /**
     * Cuantos caracteres del final podrian ser un UUID a medio llegar: los hex y guiones seguidos
     * (y una comilla invertida delante), sin pasar el largo de un UUID, y desde un borde de palabra,
     * porque un UUID no empieza pegado a otra letra: la "a" final de "Hola" no se retiene. Retener de
     * mas solo demora unos milisegundos una palabra como "cada"; retener de menos filtraria medio id.
     */
    private int colaQuePuedeSerUnId() {
        int inicio = pendiente.length();
        while (inicio > 0 && pendiente.length() - inicio < LARGO_DE_UN_UUID + 1
                && puedeSerParteDeUnId(pendiente.charAt(inicio - 1))) {
            inicio--;
        }
        for (int i = inicio; i < pendiente.length(); i++) {
            if (i == 0 || !esDePalabra(pendiente.charAt(i - 1))) {
                return pendiente.length() - i;
            }
        }
        return 0;
    }

    private static boolean puedeSerParteDeUnId(char c) {
        return Character.digit(c, 16) >= 0 || c == '-' || c == '`';
    }

    private static boolean esDePalabra(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }
}
