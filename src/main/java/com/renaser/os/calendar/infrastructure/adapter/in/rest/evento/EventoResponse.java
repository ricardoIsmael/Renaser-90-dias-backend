package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.domain.model.evento.Evento;

import java.util.List;

/** Espejo de {@code CalendarEventLite} (types.ts, app instalada). */
record EventoResponse(String id, String title, String description, String coverUrl, String startsAt,
                       Integer durationMinutes, String timezone, String locationType, String locationValue,
                       String eventType, List<EventoWireMapper.ReglaRecordatorioWire> reminderRules,
                       boolean notifyOnCreate, boolean remindByEmail, String recurrenceFrequency, String createdById,
                       String audienceType, List<String> targetRoles) {

    static EventoResponse from(Evento e, String coverUrl) {
        List<EventoWireMapper.ReglaRecordatorioWire> reminderRules = e.recordatoriosPersonalizados()
                ? e.reglasRecordatorio().stream().map(EventoWireMapper::toWireRegla).toList()
                : null;
        return new EventoResponse(
                e.id().toString(),
                e.titulo(),
                e.descripcion(),
                coverUrl,
                e.iniciaEn().toString(),
                e.duracionMinutos(),
                e.timezone().getId(),
                EventoWireMapper.toWireUbicacion(e.tipoUbicacion()),
                e.valorUbicacion(),
                e.tipoEvento().name(),
                reminderRules,
                e.notificarAlCrear(),
                e.recordarPorEmail(),
                e.recurrencia() == null ? null : EventoWireMapper.toWireFrecuencia(e.recurrencia().frecuencia()),
                e.creadoPor() == null ? null : e.creadoPor().toString(),
                EventoWireMapper.toWireAudiencia(e.tipoAudiencia()),
                e.rolesDestino().stream().map(Enum::name).toList());
    }
}
