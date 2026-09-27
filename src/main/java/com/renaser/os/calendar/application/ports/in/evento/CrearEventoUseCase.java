package com.renaser.os.calendar.application.ports.in.evento;

import com.renaser.os.calendar.domain.model.evento.Recurrencia;
import com.renaser.os.calendar.domain.model.evento.ReglaRecordatorio;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.TipoAudiencia;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.calendar.domain.model.evento.TipoUbicacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public interface CrearEventoUseCase {

    EventoVista crear(CrearEventoCommand command);

    /**
     * Solo ADMIN/ALCHEMIST crean eventos (D-186); la audiencia del comando se respeta tal cual.
     *
     * <blockquote><b>Corregido 2026-09-26 (D-186).</b> Decia que un MENTOR podia crear sesiones
     * CELULA de su propia celula y que el service le sobreescribia la audiencia
     * ({@code forceMentorCellAudience} del repo viejo). El MENTOR ya no crea eventos.</blockquote>
     */
    record CrearEventoCommand(UserId actorId, String titulo, String descripcion, Instant iniciaEn,
                               Integer duracionMinutos, ZoneId timezone, TipoUbicacion tipoUbicacion,
                               String valorUbicacion, TipoAudiencia tipoAudiencia, Integer nivelMinimoId,
                               String cursoId, UUID celulaDestinoId, TipoEvento tipoEvento, boolean notificarAlCrear,
                               boolean recordarPorEmail, boolean recordatoriosPersonalizados, Recurrencia recurrencia,
                               Set<RolUsuario> rolesDestino, List<ReglaRecordatorio> reglasRecordatorio) {

        public CrearEventoCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            Objects.requireNonNull(tipoEvento, "tipoEvento es obligatorio");
        }
    }
}
