package com.renaser.os.users.domain.model.participante;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;

/**
 * Agregado de la inscripcion al programa de 90 dias. Tabla `participantes_programa`
 * (baseline V1, ~linea 255) — ABIERTA a los 5 roles, obligatoria solo para TRAINEE
 * (la fila se crea al aprobar su cuenta, fuera del alcance de este agregado todavia:
 * ver docs/MODULO_USERS.md) y OPCIONAL para el resto ("seguimiento personal").
 *
 * <p>{@code fechaGraduacionEsperada} NO es un campo: en Postgres es columna generada
 * ({@code GENERATED ALWAYS AS (fecha_inicio + 90) STORED}) — este agregado nunca la
 * escribe, y el dominio la calcula al vuelo con {@link #fechaGraduacionEsperada()} en
 * vez de duplicar el valor.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "participanteId")
public final class ParticipacionPrograma {

    private static final int DURACION_PROGRAMA_DIAS = 90;
    /** Mismo default que la columna `participantes_programa.timezone` del baseline. */
    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");

    private final UserId participanteId;
    private UserId mentorId;
    private UUID celulaId;
    private int diaPrograma;
    private FasePrograma fase;
    private LocalDate fechaInicio;
    private Instant programaActivadoEn;
    private ZoneId timezone;
    private boolean programaCompletado;
    private int diaPostPrograma;
    private final Instant creadoEn;
    private Instant actualizadoEn;

    /**
     * Alta de "seguimiento personal" (Habitos/Rocas opcional para staff — MENTOR,
     * MENTOR_LEAD, ADMIN, ALCHEMIST). Arranca YA activada (dia 1, sin el paso pendiente que
     * si tiene el alta de un TRAINEE real via AccountRequest): replica 1:1
     * `datosDeActivacion()`/`createTraineeProfileForMentor` del backend viejo
     * (src/features/mentor/repository.ts) — dia 1, fecha de hoy, fase inicial, zona
     * 'America/Lima' por defecto.
     */
    public static ParticipacionPrograma activarSeguimientoPersonal(UserId participanteId, Clock clock) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Instant now = clock.now();
        return new ParticipacionPrograma(participanteId, null, null, 1, FasePrograma.initial(), clock.today(),
                now, ZONA_POR_DEFECTO, false, 0, now, now);
    }

    /**
     * Alta de un TRAINEE real al aprobarse su cuenta (invariante de la tabla en el
     * baseline: "la fila se crea al aprobar su cuenta, en la misma transaccion"). A
     * diferencia de {@link #activarSeguimientoPersonal}, el reloj del programa arranca
     * PAUSADO: {@code programaActivadoEn = null} — el flujo de primer login + Ficha +
     * Terminos lo activa despues. Porte literal de
     * `onboarding/service.ts::createTraineePendingActivation` (repo viejo): dia 0, fecha
     * de inicio = manana (no hoy), fase inicial, sin mentor ni celula todavia.
     */
    public static ParticipacionPrograma inscribirTraineeAprobado(UserId participanteId, Clock clock) {
        Objects.requireNonNull(participanteId, "participanteId es obligatorio");
        Instant now = clock.now();
        return new ParticipacionPrograma(participanteId, null, null, 0, FasePrograma.initial(),
                clock.today().plusDays(1), null, ZONA_POR_DEFECTO, false, 0, now, now);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static ParticipacionPrograma rehydrate(UserId participanteId, UserId mentorId, UUID celulaId,
                                                   int diaPrograma, FasePrograma fase, LocalDate fechaInicio,
                                                   Instant programaActivadoEn, ZoneId timezone,
                                                   boolean programaCompletado, int diaPostPrograma, Instant creadoEn,
                                                   Instant actualizadoEn) {
        return new ParticipacionPrograma(participanteId, mentorId, celulaId, diaPrograma, fase, fechaInicio,
                programaActivadoEn, timezone, programaCompletado, diaPostPrograma, creadoEn, actualizadoEn);
    }

    /** `fecha_inicio + 90` — NUNCA se persiste (columna generada en Postgres, se calcula al vuelo). */
    public LocalDate fechaGraduacionEsperada() {
        return fechaInicio.plusDays(DURACION_PROGRAMA_DIAS);
    }

    public void avanzarDia(Clock clock) {
        if (diaPrograma >= DURACION_PROGRAMA_DIAS) {
            return;
        }
        this.diaPrograma++;
        this.actualizadoEn = clock.now();
    }

    public void asignarMentor(UserId nuevoMentorId, Clock clock) {
        this.mentorId = Objects.requireNonNull(nuevoMentorId, "mentorId es obligatorio");
        this.actualizadoEn = clock.now();
    }

    public boolean estaActivado() {
        return programaActivadoEn != null;
    }

    @Override
    public String toString() {
        return "ParticipacionPrograma[" + participanteId + ", dia=" + diaPrograma + ", " + fase + "]";
    }
}
