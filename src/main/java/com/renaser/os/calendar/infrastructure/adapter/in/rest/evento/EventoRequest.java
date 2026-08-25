package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import jakarta.validation.constraints.NotBlank;

import java.time.Instant;
import java.util.List;

/**
 * Mismo shape para crear y editar — CreateEventPayload/UpdateEventPayload comparten forma
 * en el cliente (calendar.ts, repo viejo): el formulario siempre reenvia el evento
 * COMPLETO, nunca un PATCH parcial (CLAUDE.MD §5.4.3).
 *
 * <p>Todos los campos de audiencia/recurrencia avanzada (minLevelId/courseId/targetCellId/
 * recurrence*) son opcionales porque el cliente movil v1 no los expone (los rellena con
 * null/default via withEventDefaults() en calendar.ts) — solo los usa un futuro panel admin.
 *
 * <p>{@code notifyOnCreate}/{@code remindByEmail} son {@code Boolean} (no {@code boolean}
 * primitivo) para que puedan faltar en el JSON sin que Jackson explote con
 * {@code Cannot map `null` into type `boolean`} — mismo motivo que ya vale para
 * {@code audienceType}/{@code timezone}/{@code targetRoles}/{@code recurrenceByWeekday}
 * arriba: withEventDefaults() del cliente puede omitirlos. Default {@code false} para
 * ambos, verificado contra {@code CreateEventInput}/{@code UpdateEventInput}
 * (schema.ts, repo viejo): {@code notifyOnCreate: z.boolean().default(false)},
 * {@code remindByEmail: z.boolean().default(false)}.
 */
record EventoRequest(@NotBlank String title, @NotBlank String eventType, String description,
                      @NotBlank String startsAt, Integer durationMinutes, String timezone,
                      @NotBlank String locationType, String locationValue, String audienceType, Integer minLevelId,
                      String courseId, List<String> targetRoles, String targetCellId,
                      List<EventoWireMapper.ReglaRecordatorioWire> reminderRules, Boolean notifyOnCreate,
                      Boolean remindByEmail, String recurrenceFrequency, Integer recurrenceInterval,
                      List<Integer> recurrenceByWeekday, String recurrenceUntil, Integer recurrenceCount) {

    EventoRequest {
        if (audienceType == null || audienceType.isBlank()) {
            audienceType = "ALL_MEMBERS";
        }
        if (timezone == null || timezone.isBlank()) {
            timezone = "America/Lima";
        }
        if (targetRoles == null) {
            targetRoles = List.of();
        }
        if (recurrenceByWeekday == null) {
            recurrenceByWeekday = List.of();
        }
        if (notifyOnCreate == null) {
            notifyOnCreate = Boolean.FALSE;
        }
        if (remindByEmail == null) {
            remindByEmail = Boolean.FALSE;
        }
    }

    Instant startsAtInstant() {
        return Instant.parse(startsAt);
    }

    Instant recurrenceUntilInstant() {
        return recurrenceUntil == null || recurrenceUntil.isBlank() ? null : Instant.parse(recurrenceUntil);
    }
}
