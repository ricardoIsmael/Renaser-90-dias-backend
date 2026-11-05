package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.meet.DisponibilidadDeMeet;

/**
 * Si el formulario puede ofrecer «Crear enlace de Meet» (spec 040, FR-014). {@code motivo}: {@code APAGADA}, {@code SIN_CONFIGURAR}, {@code SIN_CUENTA}
 * o {@code CUENTA_REVOCADA}; {@code null} si se puede.
 */
record DisponibilidadDeMeetResponse(boolean disponible, String motivo) {

    static DisponibilidadDeMeetResponse from(DisponibilidadDeMeet d) {
        return new DisponibilidadDeMeetResponse(d.disponible(), d.motivo());
    }
}
