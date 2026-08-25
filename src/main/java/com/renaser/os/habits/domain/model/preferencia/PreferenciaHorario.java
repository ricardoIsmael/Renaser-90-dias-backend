package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Override del participante sobre el horario de un habito (catalogo o
 * personal) — tabla `preferencias_horario`, PK compuesta (participanteId, habitoId).
 * Un cambio dentro de la ventana de edicion gratuita (CLAUDE.MD / limits.ts,
 * WEEKLY_SCHEDULE_EDIT_LIMIT=3, FREE_SCHEDULE_EDITS_UNTIL_DAY=7 — aplicado por
 * el caso de uso, no por este agregado) rige de inmediato; fuera de ella, el
 * cambio se guarda como {@link CambioHorarioPendiente} y rige desde su fecha
 * efectiva.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"participanteId", "habitoId"})
public final class PreferenciaHorario {

    private final UserId participanteId;
    private final HabitoId habitoId;
    private LocalTime horaDisparo;
    private LocalTime horaLimite;
    private boolean recordatorioActivo;
    private Integer minutosRecordatorio;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    public static PreferenciaHorario crear(UserId participanteId, HabitoId habitoId, LocalTime horaDisparo,
                                            LocalTime horaLimite, Instant ahora) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        return new PreferenciaHorario(participanteId, habitoId, horaDisparo, horaLimite, true, null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static PreferenciaHorario rehydrate(UserId participanteId, HabitoId habitoId, LocalTime horaDisparo,
                                                LocalTime horaLimite, boolean recordatorioActivo,
                                                Integer minutosRecordatorio, Instant creadoEn, Instant actualizadoEn) {
        return new PreferenciaHorario(participanteId, habitoId, horaDisparo, horaLimite, recordatorioActivo,
                minutosRecordatorio, creadoEn, actualizadoEn);
    }

    public void aplicarAhora(LocalTime horaDisparo, LocalTime horaLimite, Instant ahora) {
        this.horaDisparo = horaDisparo;
        this.horaLimite = horaLimite;
        this.actualizadoEn = ahora;
    }

    public void actualizarRecordatorio(boolean activo, Integer minutosAntes, Instant ahora) {
        this.recordatorioActivo = activo;
        this.minutosRecordatorio = minutosAntes;
        this.actualizadoEn = ahora;
    }

    @Override
    public String toString() {
        return "PreferenciaHorario[" + participanteId + ", " + habitoId + "]";
    }
}
