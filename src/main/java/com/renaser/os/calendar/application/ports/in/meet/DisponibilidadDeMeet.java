package com.renaser.os.calendar.application.ports.in.meet;

/**
 * Si el formulario de eventos puede ofrecer «Crear enlace de Meet» (spec 040, FR-014): hay cuenta de Google activa y Soporte TI prendió
 * {@code eventos.meet}. {@code motivo} dice por qué no, para quien administra: {@code APAGADA}, {@code SIN_CUENTA}, {@code CUENTA_REVOCADA} o
 * {@code SIN_CONFIGURAR}; {@code null} cuando está disponible.
 */
public record DisponibilidadDeMeet(boolean disponible, String motivo) {

    public static DisponibilidadDeMeet siPuede() {
        return new DisponibilidadDeMeet(true, null);
    }

    public static DisponibilidadDeMeet no(String motivo) {
        return new DisponibilidadDeMeet(false, motivo);
    }
}
