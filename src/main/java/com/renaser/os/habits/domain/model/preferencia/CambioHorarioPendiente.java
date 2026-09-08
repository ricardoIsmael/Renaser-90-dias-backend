package com.renaser.os.habits.domain.model.preferencia;

import com.renaser.os.habits.domain.model.horario.VentanaDelDia;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Cambio de horario programado para el futuro — su existencia ES "hay un
 * cambio pendiente" (tabla `cambios_horario_pendientes`, PK compuesta
 * (participanteId, habitoId), FK 1:1 a {@link PreferenciaHorario}).
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = {"participanteId", "habitoId"})
public final class CambioHorarioPendiente {

    private final UserId participanteId;
    private final HabitoId habitoId;
    private final LocalTime horaDisparo;
    private final LocalTime horaLimite;
    private final Boolean recordatorioActivo;
    private final Integer minutosRecordatorio;
    private final LocalDate fechaEfectiva;
    private final Instant creadoEn;

    public static CambioHorarioPendiente programar(UserId participanteId, HabitoId habitoId, LocalTime horaDisparo,
                                                     LocalTime horaLimite, Boolean recordatorioActivo,
                                                     Integer minutosRecordatorio, LocalDate fechaEfectiva,
                                                     Instant ahora) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        Objects.requireNonNull(fechaEfectiva, "fechaEfectiva es obligatoria");
        // D-122: el cambio diferido se normaliza al programarlo, no al promoverlo. Si no, la
        // ventana que se guarda hoy y la que rige maniana serian dos datos distintos (E-159).
        LocalTime disparo = VentanaDelDia.requireHoraDisparoDentroDelDia(horaDisparo);
        return new CambioHorarioPendiente(participanteId, habitoId, disparo,
                VentanaDelDia.horaLimiteAjustada(disparo, horaLimite), recordatorioActivo,
                minutosRecordatorio, fechaEfectiva, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static CambioHorarioPendiente rehydrate(UserId participanteId, HabitoId habitoId, LocalTime horaDisparo,
                                                     LocalTime horaLimite, Boolean recordatorioActivo,
                                                     Integer minutosRecordatorio, LocalDate fechaEfectiva,
                                                     Instant creadoEn) {
        return new CambioHorarioPendiente(participanteId, habitoId, horaDisparo, horaLimite, recordatorioActivo,
                minutosRecordatorio, fechaEfectiva, creadoEn);
    }

    public boolean rigeEn(LocalDate fecha) {
        return !fecha.isBefore(fechaEfectiva);
    }

    @Override
    public String toString() {
        return "CambioHorarioPendiente[" + participanteId + ", " + habitoId + ", rige " + fechaEfectiva + "]";
    }
}
