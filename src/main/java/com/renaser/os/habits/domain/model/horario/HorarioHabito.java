package com.renaser.os.habits.domain.model.horario;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Objects;

/**
 * Horario del catalogo para un habito, valido en un rango de dias de programa
 * (tabla `horarios_habito`). Simplificacion deliberada (ver docs/MODULO_HABITS.md):
 * no incluye el escalonamiento por lotes (`habitStaggering.ts`) — esa capa vive
 * en `desbloqueos_habito`, resuelta con un caso de uso simple, sin el algoritmo
 * completo de relleno automatico del repo viejo.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "id")
public final class HorarioHabito {

    private final HorarioHabitoId id;
    private final HabitoId habitoId;
    private int diaInicio; // mutable desde 2026-08-26 (hueco #11): el panel admin puede correr el rango de dias
    private Integer diaFin;
    private TipoDia tipoDia;
    private LocalTime horaDisparo;
    private LocalTime horaLimite;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /**
     * El {@code id} entra por parametro, no se genera aca: la identidad viene del puerto
     * {@code IdGenerator} que inyecta el caso de uso ({@code HorarioHabitoAdminService.crear}).
     */
    public static HorarioHabito crear(HorarioHabitoId id, HabitoId habitoId, int diaInicio, Integer diaFin,
                                       TipoDia tipoDia, LocalTime horaDisparo, LocalTime horaLimite, Instant ahora) {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(habitoId, "habitoId es obligatorio");
        Objects.requireNonNull(tipoDia, "tipoDia es obligatorio");
        if (diaInicio < 1 || diaInicio > 90) {
            throw new IllegalArgumentException("diaInicio fuera de rango 1..90: " + diaInicio);
        }
        if (diaFin != null && diaFin < diaInicio) {
            throw new IllegalArgumentException("diaFin no puede ser anterior a diaInicio");
        }
        requireHorasCoherentes(horaDisparo, horaLimite);
        return new HorarioHabito(id, habitoId, diaInicio, diaFin, tipoDia, horaDisparo,
                horaLimite, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia. */
    public static HorarioHabito rehydrate(HorarioHabitoId id, HabitoId habitoId, int diaInicio, Integer diaFin,
                                           TipoDia tipoDia, LocalTime horaDisparo, LocalTime horaLimite,
                                           Instant creadoEn, Instant actualizadoEn) {
        return new HorarioHabito(id, habitoId, diaInicio, diaFin, tipoDia, horaDisparo, horaLimite, creadoEn,
                actualizadoEn);
    }

    public boolean aplicaEnDia(int diaPrograma, TipoDia tipoDiaDelDia) {
        boolean enRango = diaPrograma >= diaInicio && (diaFin == null || diaPrograma <= diaFin);
        boolean tipoCoincide = tipoDia == TipoDia.TODOS || tipoDia == tipoDiaDelDia;
        return enRango && tipoCoincide;
    }

    public void actualizarHoras(LocalTime horaDisparo, LocalTime horaLimite, Instant ahora) {
        requireHorasCoherentes(horaDisparo, horaLimite);
        this.horaDisparo = horaDisparo;
        this.horaLimite = horaLimite;
        this.actualizadoEn = ahora;
    }

    /**
     * Invariante del agregado, no solo una anotacion de DTO (CLAUDE.MD §5.3, encargo
     * "habits-personal-con-horario"): si las dos horas vienen cargadas, la de limite tiene
     * que ser posterior a la de disparo — si no, el habito nace vencido y nunca se puede
     * completar. {@code horaDisparo} nulo (horario "todo el dia") sigue permitido, igual que
     * hoy: esto no endurece el caso ya usado por el catalogo admin, solo cierra el caso
     * absurdo de limite &lt;= disparo cuando ambas vienen cargadas.
     */
    private static void requireHorasCoherentes(LocalTime horaDisparo, LocalTime horaLimite) {
        if (horaDisparo != null && horaLimite != null && !horaLimite.isAfter(horaDisparo)) {
            throw new IllegalArgumentException("horaLimite debe ser posterior a horaDisparo");
        }
    }

    /** Edicion administrativa del rango de dias/tipo de dia (panel admin, hueco #11). */
    public void actualizarRango(int diaInicio, Integer diaFin, TipoDia tipoDia, Instant ahora) {
        Objects.requireNonNull(tipoDia, "tipoDia es obligatorio");
        if (diaInicio < 1 || diaInicio > 90) {
            throw new IllegalArgumentException("diaInicio fuera de rango 1..90: " + diaInicio);
        }
        if (diaFin != null && diaFin < diaInicio) {
            throw new IllegalArgumentException("diaFin no puede ser anterior a diaInicio");
        }
        this.diaInicio = diaInicio;
        this.diaFin = diaFin;
        this.tipoDia = tipoDia;
        this.actualizadoEn = ahora;
    }

    @Override
    public String toString() {
        return "HorarioHabito[" + id + ", " + habitoId + ", dia " + diaInicio + "-" + diaFin + "]";
    }
}
