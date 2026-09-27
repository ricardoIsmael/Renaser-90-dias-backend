package com.renaser.os.chat.domain.model.bienvenida;

import java.util.Arrays;
import java.util.List;

/**
 * Lo que Administración y Alquimista pueden cambiar de la bienvenida automática desde la app (D-210,
 * pedido del dueño del 2026-09-27): sus tres mensajes y la portada de la tarjeta.
 *
 * <p>El nombre de cada valor es el que viaja por la API ({@code clave}) y el que guarda la bitácora
 * {@code cambios_bienvenida} (V73, columna {@code pieza} con su {@code CHECK}). Renombrar uno obliga a
 * migrar esa columna.
 */
public enum PiezaDeBienvenida {

    /** Acompaña la tarjeta en el chat de soporte (clave {@code soporte.con-la-tarjeta} del recurso). */
    SOPORTE_CON_LA_TARJETA(List.of(TextoDeBienvenida.NOMBRE)),

    /** El mensaje formal del chat de soporte (clave {@code soporte.formal}). */
    SOPORTE_FORMAL(List.of(TextoDeBienvenida.NOMBRE)),

    /** El del chat del grupo estable (clave {@code grupo}): nombra también al mentor (D-204). */
    GRUPO(List.of(TextoDeBienvenida.NOMBRE, TextoDeBienvenida.MENTOR)),

    /** La imagen de fondo de la tarjeta (sin marcadores: el nombre lo escribe el servidor encima). */
    PORTADA(List.of());

    private final List<String> marcadores;

    PiezaDeBienvenida(List<String> marcadores) {
        this.marcadores = marcadores;
    }

    /**
     * Los marcadores que el texto de esta pieza TIENE que llevar, y los únicos que puede llevar: son los
     * que el servidor reemplaza al mandarlo. Vacío para la portada.
     */
    public List<String> marcadores() {
        return marcadores;
    }

    public boolean esTexto() {
        return this != PORTADA;
    }

    /** Las tres piezas de texto, en el orden en que salen (soporte: tarjeta, formal; después, el grupo). */
    public static List<PiezaDeBienvenida> textos() {
        return Arrays.stream(values()).filter(PiezaDeBienvenida::esTexto).toList();
    }

    /**
     * La pieza de TEXTO con esa clave (la de la ruta de la API).
     *
     * @throws IllegalArgumentException si no es una de las tres (la portada tampoco: se cambia aparte)
     */
    public static PiezaDeBienvenida textoDe(String clave) {
        return textos().stream()
                .filter(pieza -> pieza.name().equals(clave))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "No hay un mensaje de bienvenida que se llame «" + clave + "». Son: "
                                + textos().stream().map(Enum::name).toList()));
    }
}
