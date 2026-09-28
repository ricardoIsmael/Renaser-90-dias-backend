package com.renaser.os.calendar.infrastructure.adapter.out.persistence.elegibilidad;

import com.renaser.os.calendar.application.ports.out.elegibilidad.ConsultarElegibilidadEventoPort;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * La Mentoria del Alquimista la ve quien esta en la audiencia que el evento declara, igual que cualquier
 * otro evento (E-362, D-213, 2026-09-27). No agrega ningun criterio propio: la audiencia la decide
 * {@code ResolverAudiencia} antes o despues de preguntar aca, y este puerto solo existe para enchufar un
 * criterio EXTRA cuando el dueño lo defina.
 *
 * <p><b>Por que no hay un criterio extra.</b> El repo viejo exigia a un aprendiz 80 % de cumplimiento
 * semanal de habitos y rocas ({@code mentoriaEligibility.ts}). Esa regla no esta confirmada para este
 * sistema: la especificacion no la menciona, y el dueño dejo el link de la mentoria de Darren (punto 8 del
 * Excel) fuera del semaforo (D-168). Es una pregunta abierta (D-213), no un dato para inventar.
 *
 * <p><b>Lo que habia antes.</b> {@code ElegibilidadEventoNoOpAdapter} respondia siempre "no elegible": una
 * Mentoria "para todos" no le aparecia a ningun aprendiz, su detalle daba 403 «No tienes acceso a este
 * evento» y no generaba ningun recordatorio (HALLAZGO-A2 de la prueba de punta a punta del 2026-09-27).
 */
@Component
class ElegibilidadSegunAudienciaAdapter implements ConsultarElegibilidadEventoPort {

    @Override
    public boolean esElegible(UserId usuarioId, TipoEvento tipoEvento) {
        return true;
    }
}
