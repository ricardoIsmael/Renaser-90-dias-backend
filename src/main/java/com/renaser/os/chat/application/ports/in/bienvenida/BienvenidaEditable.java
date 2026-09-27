package com.renaser.os.chat.application.ports.in.bienvenida;

import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;

import java.time.Instant;
import java.util.List;

/**
 * La bienvenida como la ve Administración para cambiarla (D-210): los tres mensajes y la portada, con lo
 * que sale hoy, el original y el último cambio de cada uno.
 *
 * @param activa       si las bienvenidas automáticas están prendidas ({@code BIENVENIDA_ACTIVA}); apagadas,
 *                     lo que se cambie vale recién cuando se prendan
 * @param largoMaximo  el largo máximo de un mensaje, en caracteres
 * @param textos       en el orden en que salen: el que acompaña la tarjeta, el formal y el del grupo
 */
public record BienvenidaEditable(boolean activa, int largoMaximo, List<TextoEditable> textos,
                                 PortadaEditable portada) {

    /**
     * @param texto        el que sale hoy
     * @param original     el del recurso del repo ({@code bienvenida/mensajes.yaml})
     * @param cambiado     si hoy sale uno que guardó Administración
     * @param marcadores   los que el texto tiene que llevar, y los únicos que puede llevar
     * @param ultimoCambio {@code null} si nunca se cambió
     */
    public record TextoEditable(PiezaDeBienvenida pieza, String texto, String original, boolean cambiado,
                                List<String> marcadores, UltimoCambio ultimoCambio) {
    }

    /**
     * @param cambiada       si la tarjeta sale sobre una portada que subió Administración
     * @param sePuedeCambiar {@code false} si el servidor no guarda imágenes (almacenamiento de marcador)
     */
    public record PortadaEditable(boolean cambiada, boolean sePuedeCambiar, UltimoCambio ultimoCambio) {
    }

    /**
     * @param por              el nombre de quien lo hizo; {@code null} si su cuenta ya no existe
     * @param volvioAlOriginal si el último cambio fue volver al original
     */
    public record UltimoCambio(String por, Instant en, boolean volvioAlOriginal) {
    }
}
