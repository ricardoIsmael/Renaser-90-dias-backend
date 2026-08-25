package com.renaser.os.calendar.application.ports.out.elegibilidad;

import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.shared.domain.UserId;

/**
 * Elegibilidad especial por TIPO de evento (hoy solo {@code MENTORIA_ALQUIMISTA} —
 * {@link com.renaser.os.calendar.domain.model.evento.ReglasPorTipoEvento#requiereElegibilidad}).
 *
 * <p><b>PENDIENTE DE INTEGRACION</b> — ver docs/MODULO_CALENDAR.md §6. El repo viejo
 * (mentoriaEligibility.ts) calcula esto con el % de cumplimiento SEMANAL de habitos+rocas
 * (Ley VI), datos que hoy viven en `habits`/`rocks`, no en `calendar` ni en el contrato
 * publico que este encargo me dio. El adaptador NoOp de esta version devuelve
 * {@code false} (nunca datos inventados, CLAUDE.MD §0.6) — un TRAINEE no vera ninguna
 * sesion de Mentoria con el Alquimista hasta que se conecte el dato real. El bypass de
 * ADMIN/ALCHEMIST/MENTOR (siempre elegibles, `rol_privilegiado` en el repo viejo) se
 * resuelve en el servicio de aplicacion ANTES de llamar a este puerto — nunca se pregunta
 * para esos roles.
 */
public interface ConsultarElegibilidadEventoPort {

    boolean esElegible(UserId usuarioId, TipoEvento tipoEvento);
}
