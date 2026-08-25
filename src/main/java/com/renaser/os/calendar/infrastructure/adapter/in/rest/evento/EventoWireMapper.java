package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.domain.model.confirmacion.EstadoConfirmacion;
import com.renaser.os.calendar.domain.model.evento.FrecuenciaRecurrencia;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

/**
 * Traduccion wire (ingles, app instalada) <-> dominio (español, D-21/D-36). Vive SOLO en
 * esta frontera — nunca en dominio ni persistencia (CLAUDE.MD §5.4.4/§5.4.5). Literales
 * sacados EXACTOS del repo viejo: {@code prisma/schema.prisma} (EventAudienceType,
 * EventLocationType, RecurrenceFrequency, RsvpStatus) y {@code reminders.ts} (ReminderRule.kind).
 *
 * <p>{@code TipoEvento} (MENTORIA_ALQUIMISTA/ESPONTANEO/...) NO se traduce: sus literales
 * ya son identicos en dominio, base y wire (ver javadoc del enum).
 */
final class EventoWireMapper {

    private EventoWireMapper() {
    }

    // ─── TipoAudiencia / EventAudienceType ─────────────────────────────────────

    static String toWireAudiencia(TipoAudiencia tipo) {
        return switch (tipo) {
            case TODOS -> "ALL_MEMBERS";
            case NIVEL_MINIMO -> "MIN_LEVEL";
            case CURSO -> "COURSE";
            case ROLES -> "ROLES";
            case CELULA -> "CELL";
        };
    }

    static TipoAudiencia fromWireAudiencia(String wire) {
        return switch (wire) {
            case "ALL_MEMBERS" -> TipoAudiencia.TODOS;
            case "MIN_LEVEL" -> TipoAudiencia.NIVEL_MINIMO;
            case "COURSE" -> TipoAudiencia.CURSO;
            case "ROLES" -> TipoAudiencia.ROLES;
            case "CELL" -> TipoAudiencia.CELULA;
            default -> throw new IllegalArgumentException("audienceType invalido: " + wire);
        };
    }

    // ─── TipoUbicacion / EventLocationType ─────────────────────────────────────

    static String toWireUbicacion(TipoUbicacion tipo) {
        return switch (tipo) {
            case LLAMADA_INTERNA -> "INTERNAL_CALL";
            case WEBINAR -> "WEBINAR";
            case ZOOM -> "ZOOM";
            case MEET -> "MEET";
            case DIRECCION -> "ADDRESS";
            case ENLACE -> "LINK";
        };
    }

    static TipoUbicacion fromWireUbicacion(String wire) {
        return switch (wire) {
            case "INTERNAL_CALL" -> TipoUbicacion.LLAMADA_INTERNA;
            case "WEBINAR" -> TipoUbicacion.WEBINAR;
            case "ZOOM" -> TipoUbicacion.ZOOM;
            case "MEET" -> TipoUbicacion.MEET;
            case "ADDRESS" -> TipoUbicacion.DIRECCION;
            case "LINK" -> TipoUbicacion.ENLACE;
            default -> throw new IllegalArgumentException("locationType invalido: " + wire);
        };
    }

    // ─── FrecuenciaRecurrencia / RecurrenceFrequency ───────────────────────────

    static String toWireFrecuencia(FrecuenciaRecurrencia tipo) {
        return switch (tipo) {
            case DIARIA -> "DAILY";
            case SEMANAL -> "WEEKLY";
            case MENSUAL -> "MONTHLY";
        };
    }

    static FrecuenciaRecurrencia fromWireFrecuencia(String wire) {
        return switch (wire) {
            case "DAILY" -> FrecuenciaRecurrencia.DIARIA;
            case "WEEKLY" -> FrecuenciaRecurrencia.SEMANAL;
            case "MONTHLY" -> FrecuenciaRecurrencia.MENSUAL;
            default -> throw new IllegalArgumentException("recurrenceFrequency invalido: " + wire);
        };
    }

    // ─── EstadoConfirmacion / RsvpStatus ────────────────────────────────────────

    static String toWireEstadoConfirmacion(EstadoConfirmacion estado) {
        return switch (estado) {
            case ASISTE -> "GOING";
            case NO_ASISTE -> "NOT_GOING";
            case QUIZAS -> "MAYBE";
        };
    }

    static EstadoConfirmacion fromWireEstadoConfirmacion(String wire) {
        return switch (wire) {
            case "GOING" -> EstadoConfirmacion.ASISTE;
            case "NOT_GOING" -> EstadoConfirmacion.NO_ASISTE;
            case "MAYBE" -> EstadoConfirmacion.QUIZAS;
            default -> throw new IllegalArgumentException("status (rsvp) invalido: " + wire);
        };
    }

    // ─── Set<DayOfWeek> / recurrenceByWeekday (1=lunes..7=domingo, ISO — identico al wire) ──

    static List<Integer> toWireDiasSemana(java.util.Set<DayOfWeek> dias) {
        return dias.stream().map(DayOfWeek::getValue).sorted().toList();
    }

    static java.util.Set<DayOfWeek> fromWireDiasSemana(List<Integer> wire) {
        if (wire == null) {
            return java.util.Set.of();
        }
        return wire.stream().map(DayOfWeek::of).collect(java.util.stream.Collectors.toSet());
    }

    // ─── ReglaRecordatorio / ReminderRule ───────────────────────────────────────

    /** {@code value} es {@code Object} y no {@code JsonNode}: el ObjectMapper real en
     * runtime es Jackson 3 ({@code tools.jackson.databind}), incompatible con el tipo de
     * Jackson 2 ({@code com.fasterxml.jackson.databind}) — encontrado probando el endpoint,
     * cualquier request con reminderRules devolvia 500. La deserializacion por defecto de
     * un campo {@code Object} ya resuelve numero/texto sin depender de una libreria. */
    record ReglaRecordatorioWire(String kind, Object value) {
    }

    static ReglaRecordatorioWire toWireRegla(ReglaRecordatorio r) {
        return switch (r.tipo()) {
            case MINUTOS_ANTES -> new ReglaRecordatorioWire("minutesBefore", r.valorNumero());
            case DIAS_ANTES -> new ReglaRecordatorioWire("daysBefore", r.valorNumero());
            case HORA_DEL_DIA -> new ReglaRecordatorioWire("timeOfDay", r.valorHora().toString());
        };
    }

    static ReglaRecordatorio fromWireRegla(int orden, ReglaRecordatorioWire w) {
        return switch (w.kind()) {
            case "minutesBefore" -> ReglaRecordatorio.minutosAntes(orden, asInt(w.value()));
            case "daysBefore" -> ReglaRecordatorio.diasAntes(orden, asInt(w.value()));
            case "timeOfDay" -> ReglaRecordatorio.horaDelDia(orden, LocalTime.parse(String.valueOf(w.value())));
            default -> throw new IllegalArgumentException("reminderRule.kind invalido: " + w.kind());
        };
    }

    private static int asInt(Object value) {
        if (value instanceof Number numero) {
            return numero.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }
}
