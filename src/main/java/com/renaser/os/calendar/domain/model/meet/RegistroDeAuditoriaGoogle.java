package com.renaser.os.calendar.domain.model.meet;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.UUID;

/**
 * Una fila de la auditoría de la cuenta de Google (spec 040, FR-016): qué se hizo, quién (o «el sistema»), sobre qué evento, cómo salió y
 * qué código HTTP devolvió Google. NO guarda tokens, ni el cuerpo de lo que respondió Google, ni el enlace completo de la sala.
 *
 * @param actor       {@code null} = lo hizo el sistema (el proceso en segundo plano)
 * @param eventoId    {@code null} en las acciones de la cuenta (conectar, desconectar, revocada)
 * @param codigoHttp  el que devolvió Google; {@code null} si no hubo llamada
 * @param correlacion une las filas de una misma operación (un guardado que toca varias fechas)
 */
public record RegistroDeAuditoriaGoogle(UUID id, Instant en, AccionDeAuditoriaGoogle accion, UserId actor, UUID eventoId,
                                         boolean exito, Integer codigoHttp, String correlacion) {

    public static final String ACTOR_SISTEMA = "sistema";
}
