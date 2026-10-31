package com.renaser.os.calendar.application.ports.out.elegibilidad;

import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

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
 *
 * <blockquote><b>Corregido 2026-09-27 (E-362, D-213).</b> Con el NoOp en {@code false}, ninguna Mentoria del
 * Alquimista le llegaba a ningun aprendiz, aunque fuera "para todos". El adaptador pasa a ser
 * {@code ElegibilidadSegunAudienciaAdapter}: la audiencia que el evento declara decide, sin criterio extra.
 * El 80 % semanal del repo viejo NO esta confirmado para este sistema y queda como pregunta para el dueño;
 * si lo confirma, se implementa en otro adaptador de este mismo puerto.</blockquote>
 *
 * <blockquote><b>Corregido 2026-10-30 (D-293, cierra la pregunta abierta de D-213).</b> El dueño decidio el criterio
 * extra: un APRENDIZ ve la Mentoria del Alquimista (lista, calendario, «Mi agenda», «Proximo evento» de Hoy, detalle,
 * responder «Voy», avisos y recordatorios) solo si su semaforo vigente (D-168, el mismo que Hoy le muestra) esta en
 * VERDE; amarillo, rojo, sin datos o sin semaforo no la ve. Lo implementa {@code ElegibilidadPorSemaforoAdapter}, que
 * reemplaza a {@code ElegibilidadSegunAudienciaAdapter}. Se evalua con el semaforo del momento de la pregunta; un
 * recordatorio ya generado se cancela al despacharlo si la persona dejo de estar en verde
 * ({@code RecordatorioEvento#MOTIVO_NO_ELEGIBLE}). El 80 % semanal del repo viejo, que arriba sigue como historia, es
 * hoy ese verde (≥ 80 %). <b>Solo se pregunta por aprendices:</b> el personal (guia, mentor, lider, admin, alquimista,
 * Soporte TI) pasa siempre y lo resuelven {@code AccesoEventoService} y {@code AudienciaDelEventoService} antes de
 * llamar aca. Los demas tipos de evento no agregan criterio.</blockquote>
 */
public interface ConsultarElegibilidadEventoPort {

    boolean esElegible(UserId aprendiz, TipoEvento tipoEvento);

    /**
     * De estos aprendices, los elegibles para el tipo de evento. Es la forma que usan los barridos (recordatorios,
     * avisos, despacho, la hoja «Quien respondio»): una lectura para toda la coleccion, nunca una por persona.
     */
    default Set<UserId> elegiblesEntre(Collection<UserId> aprendices, TipoEvento tipoEvento) {
        return aprendices.stream().filter(aprendiz -> esElegible(aprendiz, tipoEvento))
                .collect(Collectors.toUnmodifiableSet());
    }
}
