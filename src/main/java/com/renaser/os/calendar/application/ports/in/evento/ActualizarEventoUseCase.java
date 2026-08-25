package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.domain.model.evento.EventoId;
import com.renaser.os.calendar.domain.model.evento.Recurrencia;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
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
                                    Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio) {

        public ActualizarEventoCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(eventoId, "eventoId es obligatorio");
        }
    }
}
