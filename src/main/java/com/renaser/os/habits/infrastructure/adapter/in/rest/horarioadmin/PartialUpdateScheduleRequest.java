package com.renaser.os.habits.infrastructure.adapter.in.rest.horarioadmin;

import tools.jackson.databind.JsonNode;

import java.time.LocalTime;

/**
 * Parseo a mano de {@code UpdateScheduleInput} (PATCH real: clave ausente = no tocar,
 * clave presente en {@code null} = limpiar) — ver el javadoc de
 * {@code HorarioHabitoAdminController.actualizar} sobre por que esto no es un record
 * comun con {@code @RequestBody}.
 */
record PartialUpdateScheduleRequest(Integer startDay, Integer endDay, boolean limpiarEndDay,
                                     DayTypeDto dayType, LocalTime defaultTriggerTime, boolean limpiarHoraDisparo,
                                     LocalTime defaultLimitTime, boolean limpiarHoraLimite) {

    static PartialUpdateScheduleRequest from(JsonNode body) {
        Integer startDay = body.hasNonNull("startDay") ? body.get("startDay").asInt() : null;
        Integer endDay = body.hasNonNull("endDay") ? body.get("endDay").asInt() : null;
        DayTypeDto dayType = body.hasNonNull("dayType") ? DayTypeDto.valueOf(body.get("dayType").asText()) : null;
        LocalTime disparo = body.hasNonNull("defaultTriggerTime")
                ? LocalTime.parse(body.get("defaultTriggerTime").asText()) : null;
        LocalTime limite = body.hasNonNull("defaultLimitTime")
                ? LocalTime.parse(body.get("defaultLimitTime").asText()) : null;
        return new PartialUpdateScheduleRequest(startDay, endDay, esNullExplicito(body, "endDay"), dayType, disparo,
                esNullExplicito(body, "defaultTriggerTime"), limite, esNullExplicito(body, "defaultLimitTime"));
    }

    private static boolean esNullExplicito(JsonNode body, String campo) {
        return body.has(campo) && body.get(campo).isNull();
    }
}
