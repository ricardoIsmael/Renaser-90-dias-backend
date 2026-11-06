package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.meet.ResumenDeMeet;
import com.renaser.os.calendar.domain.model.evento.Evento;

import com.renaser.os.calendar.application.ports.in.audiencia.PersonaConvocable;
import com.renaser.os.calendar.application.ports.in.audiencia.ResumenDeAudiencia;
import java.util.List;

/**
 * Espejo de {@code CalendarEventLite} (types.ts, app instalada). {@code formation} (spec 024) es el código de la formación del evento: la app de
 * Administración rotula con él cada fila cuando lee «Todas»; un cliente anterior lo ignora.
 */
record EventoResponse(String id, String title, String description, String coverUrl, String startsAt,
                       Integer durationMinutes, String timezone, String locationType, String locationValue,
                       String eventType, List<EventoWireMapper.ReglaRecordatorioWire> reminderRules,
                       boolean notifyOnCreate, boolean remindByEmail, String recurrenceFrequency, String createdById,
                       String audienceType, List<String> targetRoles, String formation, MeetResponse meet,
                       AudienciaResponse audience, PersonaResponse organizer) {

    /**
     * «Para quién es» (spec 044, contrato §3): solo para quien creó el evento o lo gestiona (FR-018). Para cualquier otro, {@code null}: el invitado
     * no sabe a quién más se invitó ni por qué (FR-016).
     */
    record AudienciaResponse(List<ParteResponse> parts, int total) {

        static AudienciaResponse from(ResumenDeAudiencia r) {
            return r == null ? null : new AudienciaResponse(r.partes().stream().map(ParteResponse::from).toList(), r.total());
        }
    }

    record ParteResponse(String kind, String id, String name, String color, int count, String resolvedAt, List<PersonaResponse> people) {

        static ParteResponse from(ResumenDeAudiencia.Parte p) {
            return new ParteResponse(AudienciaWire.toWireTipo(p.tipo()), p.id() == null ? null : p.id().toString(), p.nombre(),
                    p.color() == null ? null : AudienciaWire.toWireColor(p.color()), p.cuantos(),
                    p.resueltoEn() == null ? null : p.resueltoEn().toString(), p.personas().stream().map(PersonaResponse::from).toList());
        }
    }

    /** Una persona con su nombre, foto, rol en palabras y grupo (el organizador, FR-019; y las personas por nombre de la audiencia). */
    record PersonaResponse(String id, String name, String photoUrl, String role, String groupName) {

        static PersonaResponse from(PersonaConvocable p) {
            return p == null ? null : new PersonaResponse(p.id().toString(), p.nombre(), p.fotoUrl(), p.rol(), p.grupo());
        }
    }

    /** El mismo evento con su audiencia y su organizador (spec 044). */
    EventoResponse conAudiencia(ResumenDeAudiencia audiencia, PersonaConvocable organizador) {
        return new EventoResponse(id, title, description, coverUrl, startsAt, durationMinutes, timezone, locationType, locationValue,
                eventType, reminderRules, notifyOnCreate, remindByEmail, recurrenceFrequency, createdById, audienceType,
                targetRoles, formation, meet, AudienciaResponse.from(audiencia), PersonaResponse.from(organizador));
    }

    /**
     * Cómo van las salas de Meet del evento (spec 040): solo para ADMIN y ALCHEMIST y solo si el evento pide el enlace automático; en cualquier
     * otro caso la respuesta no lo lleva ({@code null}, y el aprendiz, el mentor y el líder no ven nada nuevo, SC-007).
     */
    record MeetResponse(String estado, String acceso, boolean accesoAplicado, String mensaje, int fechasConSala,
                        int fechasConProblema, String avisoDelPlan) {

        static MeetResponse from(ResumenDeMeet r) {
            return r == null ? null : new MeetResponse(r.estado().name(), r.acceso().name(), r.accesoAplicado(), r.mensaje(),
                    r.fechasConSala(), r.fechasConProblema(), r.avisoDelPlan());
        }
    }

    static EventoResponse from(Evento e, String coverUrl) {
        return from(e, coverUrl, null);
    }

    /** El mismo evento con el enlace de Meet de UNA fecha, que reemplaza al del evento (FR-017). */
    EventoResponse conUbicacion(String valor) {
        return new EventoResponse(id, title, description, coverUrl, startsAt, durationMinutes, timezone, locationType, valor,
                eventType, reminderRules, notifyOnCreate, remindByEmail, recurrenceFrequency, createdById, audienceType,
                targetRoles, formation, meet, audience, organizer);
    }

    static EventoResponse from(Evento e, String coverUrl, ResumenDeMeet meet) {
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
                e.rolesDestino().stream().map(Enum::name).toList(),
                e.formacion().codigo(),
                MeetResponse.from(meet),
                null,
                null);
    }
}
