package com.renaser.os.calendar.application.ports.in.asistencia;

import com.renaser.os.calendar.domain.model.asistencia.MarcaDeAsistencia;
import com.renaser.os.calendar.domain.model.asistencia.RespuestaAnterior;
import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * Una persona de la lista de una ocurrencia: la audiencia del evento, más quien respondió o fue marcado
 * aunque no esté en ella (D-256).
 *
 * @param respuesta    lo último que respondió; {@code null} = no respondió
 * @param respondidaEn cuándo dio esa respuesta; {@code null} si no respondió
 * @param historial    sus respuestas desde la V93, de la más vieja a la más nueva (la última es la vigente)
 * @param marca        cómo llegó; {@code null} = ausente / sin marcar
 */
public record PersonaConvocada(UserId id, String nombre, String avatarUrl, EstadoConfirmacion respuesta,
                               Instant respondidaEn, List<RespuestaAnterior> historial, MarcaDeAsistencia marca) {
}
