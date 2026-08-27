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
    /** `tipo_meta` — nullable, ver javadoc de {@link TipoMeta}. Sin setter propio todavia:
     * ningun caso de uso de este agregado lo escribe hoy (hueco #3, onboarding). */
    private TipoMeta tipoMeta;
    /** `nombre_reto_personal` — self-editable via {@link #renombrarRetoPersonal}. */
    private String nombreRetoPersonal;
    /** `programa_completado_en` — ver docs/FEATURE_POST_PROGRAM.md. Sin setter propio
     * todavia: ningun caso de uso de este agregado marca la graduacion hoy. */
    private Instant programaCompletadoEn;

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
                now, ZONA_POR_DEFECTO, false, 0, now, now, null, null, null);
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
                clock.today().plusDays(1), null, ZONA_POR_DEFECTO, false, 0, now, now, null, null, null);
    }

    /** Firma historica (12 campos, sin tipoMeta/nombreRetoPersonal/programaCompletadoEn): se
     * conserva para no obligar a los llamadores existentes a agregar 3 campos que la
     * mayoria no necesita rehidratar. */
    public static ParticipacionPrograma rehydrate(UserId participanteId, UserId mentorId, UUID celulaId,
                                                   int diaPrograma, FasePrograma fase, LocalDate fechaInicio,
                                                   Instant programaActivadoEn, ZoneId timezone,
                                                   boolean programaCompletado, int diaPostPrograma, Instant creadoEn,
                                                   Instant actualizadoEn) {
        return rehydrate(participanteId, mentorId, celulaId, diaPrograma, fase, fechaInicio, programaActivadoEn,
                timezone, programaCompletado, diaPostPrograma, creadoEn, actualizadoEn, null, null, null);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static ParticipacionPrograma rehydrate(UserId participanteId, UserId mentorId, UUID celulaId,
                                                   int diaPrograma, FasePrograma fase, LocalDate fechaInicio,
                                                   Instant programaActivadoEn, ZoneId timezone,
                                                   boolean programaCompletado, int diaPostPrograma, Instant creadoEn,
                                                   Instant actualizadoEn, TipoMeta tipoMeta,
                                                   String nombreRetoPersonal, Instant programaCompletadoEn) {
        return new ParticipacionPrograma(participanteId, mentorId, celulaId, diaPrograma, fase, fechaInicio,
                programaActivadoEn, timezone, programaCompletado, diaPostPrograma, creadoEn, actualizadoEn,
                tipoMeta, nombreRetoPersonal, programaCompletadoEn);
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

    /**
     * Ajuste operativo de un ADMIN/ALCHEMIST (panel admin de aprendices, gap #7 de
     * docs/PLAN_INTEGRACION_FRONTEND.md) — a diferencia de {@link #avanzarDia}, que solo
     * incrementa de a 1 (el paso normal del reloj del programa), esto fija el dia
     * exacto que pide un operador humano (ej. corregir un desfase). NO es una regla de
     * negocio nueva: el limite [0, 90] es la misma invariante que ya impone
     * {@link #avanzarDia} (nunca supera {@value #DURACION_PROGRAMA_DIAS}), aplicada
     * tambien al piso.
     */
    public void fijarDia(int nuevoDia, Clock clock) {
        if (nuevoDia < 0 || nuevoDia > DURACION_PROGRAMA_DIAS) {
            throw new IllegalArgumentException(
                    "diaPrograma debe estar entre 0 y " + DURACION_PROGRAMA_DIAS + ", recibido: " + nuevoDia);
        }
        this.diaPrograma = nuevoDia;
        this.actualizadoEn = clock.now();
    }

    public void asignarMentor(UserId nuevoMentorId, Clock clock) {
        this.mentorId = Objects.requireNonNull(nuevoMentorId, "mentorId es obligatorio");
        this.actualizadoEn = clock.now();
    }

    public boolean estaActivado() {
        return programaActivadoEn != null;
    }

    /**
     * U-05 (CLAUDE.MD §5.3.3, `UpdateTraineeProfileUseCase`): el unico campo de este
     * agregado que el propio participante edita directamente. {@code null} no borra el
     * valor — el comando de aplicacion ya filtra "no cambiar" antes de llegar aca (mismo
     * criterio que {@code User.rename}, nunca se llama con null desde el caso de uso).
     */
    public void renombrarRetoPersonal(String nuevoNombre, Clock clock) {
        this.nombreRetoPersonal = nuevoNombre;
        this.actualizadoEn = clock.now();
    }

    @Override
    public String toString() {
        return "ParticipacionPrograma[" + participanteId + ", dia=" + diaPrograma + ", " + fase + "]";
    }
}
