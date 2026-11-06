package com.renaser.os.calendar.application.ports.out.meet;

import com.renaser.os.calendar.domain.model.meet.transcripcion.LecturaDeReunion;

import java.time.Instant;

/**
 * Lo que pasó en una sala de Meet (spec 047, FR-003, FR-004): las conferencias de la sala que empezaron dentro de la ventana, con su
 * transcripción armada y el enlace a la grabación. Meet REST API v2 con la cuenta de la spec 040 y el permiso {@code meetings.space.readonly}.
 *
 * <p>Lanza {@link CuentaGoogleNoConectadaException} sin cuenta, {@link CuentaGoogleRevocadaException} si el permiso ya no vale,
 * {@link GoogleNoRespondeException} si conviene reintentar y {@link GoogleRechazoException} si Google no lo deja (403: falta el permiso).
 */
public interface ReunionesDeMeetPort {

    LecturaDeReunion leer(String enlaceDeLaSala, Instant desde, Instant hasta);
}
