package com.renaser.os.habits.application.ports.in.acompanamiento;

import com.renaser.os.habits.application.ports.in.radar.ConsultarHistorialRadarUseCase.HistorialRadarPage;

import java.time.Instant;

/**
 * El Codigo Renaser de un alumno —el registro horario de cuatro preguntas de los dias 1 al 7—
 * leido por quien lo acompaña (2026-09-15).
 *
 * <p><b>Esto abre algo que hasta hoy era estrictamente privado.</b> {@code RadarService} clava
 * {@code requireSelf} en sus tres operaciones y ni siquiera existia una via de admin: el sistema
 * entero estaba construido para que nadie mas que la propia persona leyera ese texto, que es
 * intimo por definicion ("que evito", "que siento"). Se abre por pedido explicito del dueño del
 * proyecto, y se abre por la puerta mas angosta posible: solo el acompañante VIGENTE del grupo al
 * que el alumno pertenece hoy, y solo de lectura.
 *
 * <p>Va en un caso de uso propio, y no como un parametro del historial que ya existe, para que
 * quitarlo sea borrar archivos y no desarmar el autoservicio.
 */
public interface ConsultarCodigoRenaserDeAlumnoUseCase {

    HistorialRadarPage codigoRenaserDeAlumno(ConsultaDeAcompanante consulta, Instant cursor, int tamanoPagina);
}
