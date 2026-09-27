package com.renaser.os.habits.application.ports.in.registro;

import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

public interface ConsultarTracksDelDiaUseCase {

    /** Autoservicio: actorId debe ser el propio participanteId (ver CLAUDE.MD §5.3.4, requireSelf). */
    List<RegistroHabito> consultar(UserId actorId, UserId participanteId, LocalDate fecha);

    /**
     * Lo mismo que {@link #consultar}, pero devuelve ademas la zona del participante, leida en la
     * MISMA consulta de progreso que autoriza (V-5, D-180). Antes la proyeccion de
     * {@code GET /habit-tracks/today} leia ese progreso tres veces por pedido: una para saber que
     * dia es hoy, otra dentro de {@code requireSelf} y otra para la ventana de entrega.
     */
    RegistrosDelDia consultarEnSuZona(UserId actorId, UserId participanteId, LocalDate fecha);

    /**
     * Los registros de HOY, con "hoy" resuelto en la zona del participante y nunca en la del
     * servidor (E-91, E-105). Misma autorizacion que {@link #consultar}.
     */
    RegistrosDelDia consultarHoy(UserId actorId, UserId participanteId);

    /** Los registros de un dia, la fecha que se consulto y la zona en que vive el participante. */
    record RegistrosDelDia(List<RegistroHabito> registros, LocalDate fecha, ZoneId zona) {
    }
}
