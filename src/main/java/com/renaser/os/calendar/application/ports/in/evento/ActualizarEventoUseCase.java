package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.application.ports.in.audiencia.PedidoDeAudiencia;
import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Recurrencia;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public interface ActualizarEventoUseCase {

    /** Reenvio completo del formulario (mismo shape que crear) — UpdateEventInput = CreateEventInput en el repo viejo. */
    EventoVista actualizar(ActualizarEventoCommand command);

    record ActualizarEventoCommand(UserId actorId, EventoId eventoId, String titulo, String descripcion,
                                    Instant iniciaEn, Integer duracionMinutos, ZoneId timezone,
                                    TipoUbicacion tipoUbicacion, String valorUbicacion, TipoAudiencia tipoAudiencia,
                                    Integer nivelMinimoId, String cursoId, UUID celulaDestinoId,
                                    boolean notificarAlCrear, boolean recordarPorEmail,
                                    boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                                    Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio,
                                    AccesoSalaMeet accesoSalaMeet, PedidoDeAudiencia audiencia) {

        public ActualizarEventoCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(eventoId, "eventoId es obligatorio");
        }

        /** Sin tocar la audiencia elegida (spec 044): si el evento la tiene, se conserva (FR-030). */
        public ActualizarEventoCommand(UserId actorId, EventoId eventoId, String titulo, String descripcion,
                                       Instant iniciaEn, Integer duracionMinutos, ZoneId timezone,
                                       TipoUbicacion tipoUbicacion, String valorUbicacion, TipoAudiencia tipoAudiencia,
                                       Integer nivelMinimoId, String cursoId, UUID celulaDestinoId,
                                       boolean notificarAlCrear, boolean recordarPorEmail,
                                       boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                                       Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio,
                                       AccesoSalaMeet accesoSalaMeet) {
            this(actorId, eventoId, titulo, descripcion, iniciaEn, duracionMinutos, timezone, tipoUbicacion,
                    valorUbicacion, tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, notificarAlCrear,
                    recordarPorEmail, recordatoriosPersonalizados, recurrencia, rolesDestino, reglasRecordatorio,
                    accesoSalaMeet, null);
        }

        /**
         * Sin pedir el enlace de Meet automático (spec 040): {@code accesoSalaMeet} nulo. Con valor, el evento queda de Meet con su sala creada por el
         * servidor y {@code valorUbicacion} es el enlace que ya tenía (o nulo si todavía no existe).
         */
        public ActualizarEventoCommand(UserId actorId, EventoId eventoId, String titulo, String descripcion,
                                       Instant iniciaEn, Integer duracionMinutos, ZoneId timezone,
                                       TipoUbicacion tipoUbicacion, String valorUbicacion, TipoAudiencia tipoAudiencia,
                                       Integer nivelMinimoId, String cursoId, UUID celulaDestinoId,
                                       boolean notificarAlCrear, boolean recordarPorEmail,
                                       boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                                       Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio) {
            this(actorId, eventoId, titulo, descripcion, iniciaEn, duracionMinutos, timezone, tipoUbicacion,
                    valorUbicacion, tipoAudiencia, nivelMinimoId, cursoId, celulaDestinoId, notificarAlCrear,
                    recordarPorEmail, recordatoriosPersonalizados, recurrencia, rolesDestino, reglasRecordatorio, null, null);
        }
    }
}
