package com.renaser.os.habits.application.ports.out.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Bitacora append-only (`historial_cambios_horario`) — ES el contador de la cuota semanal
 * de edicion (WEEKLY_SCHEDULE_EDIT_LIMIT=3 habitos DISTINTOS por semana de programa,
 * limits.ts del repo viejo). Escribir acá y no cobrar cupo serian dos verdades distintas
 * del mismo hecho — por eso solo se registra en la rama de aplicacion INMEDIATA, nunca en
 * la diferida (un cambio programado no gasta cupo).
 */
public interface HistorialCambioHorarioPort {

    /** Habitos DISTINTOS cambiados desde esa fecha (inclusive) — la cuota es por habito, no por edicion. */
    List<HabitoId> distintosHabitosCambiadosDesde(UserId participanteId, LocalDate desde);

    void registrar(UserId participanteId, HabitoId habitoId, LocalDate cambiadoEl, LocalTime horaDisparo,
                    LocalTime horaLimite, Instant ahora);
}
