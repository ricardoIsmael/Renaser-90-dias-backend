package com.renaser.os.calendar.domain.model.evento;

/**
 * Tipo Postgres {@code tipo_regla_recordatorio}. Wire (app instalada, D-36, reminders.ts
 * {@code ReminderRule.kind}): minutesBefore/daysBefore/timeOfDay.
 */
public enum TipoReglaRecordatorio {

    MINUTOS_ANTES,
    DIAS_ANTES,
    HORA_DEL_DIA
}
