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
import java.time.temporal.ChronoUnit;
import java.util.List;
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
 * vez de duplicar el valor. <b>Desde V20 la columna generada quedo obsoleta</b>: no
 * contempla {@link #diasAjuste}, y el metodo del dominio si.
 *
 * <p><b>El reloj del programa es DERIVADO, no incremental</b> (V20, BITACORA E-91).
 * {@link #diaPrograma} no se incrementa: es la materializacion de una cuenta con fechas
 * — {@link #diaProgramaDerivado(LocalDate)}. Eso lo vuelve idempotente y capaz de
 * recuperarse solo: si nadie sincronizo por tres dias, la primera corrida deja el dia
 * correcto en vez de quedar tres dias atrasado para siempre. El campo se sigue
 * materializando porque siete modulos lo leen por {@code ParticipacionProgramaFinder}
 * sin conocer la zona horaria del participante.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "participanteId")
public final class ParticipacionPrograma {

    private static final int DURACION_PROGRAMA_DIAS = 90;
    /** Mismo default que la columna `participantes_programa.timezone` del baseline. */
    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");
    /** Maximo de dias tras firmar Terminos que el aprendiz puede esperar para elegir
     * su Dia 1 (D-66): hoy, +1, +2 o +3 — 4 opciones, nunca "sin elegir". */
    private static final int MAX_DIAS_ESPERA_ACTIVACION = 3;

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
    /** `dia_programa_avanzado_el` — idempotencia del cron nocturno (QA-33, D-66): el
     * dia LOCAL (zona del participante) en que se avanzo por ultima vez `diaPrograma`.
     * {@code null} = todavia no lo toco el cron (recien inscripto, o recien activado
     * el mismo dia por {@link #activarPrograma}, que lo fija a mano). Campo agregado
     * al declararse AL FINAL a proposito: evita reordenar el constructor generado por
     * Lombok y romper las llamadas posicionales existentes de {@link #rehydrate}. */
    private LocalDate diaProgramaAvanzadoEl;
    /** `dias_ajuste_programa` (V20) — dias de calendario que NO cuentan para el programa.
     * Positivo = el participante RETROCEDE (viajo, se enfermo: se lo devuelve a un dia
     * anterior sin castigarle el puntaje ya ganado); negativo = se lo ADELANTA. Es el
     * unico knob del reloj: {@link #fijarDia} no escribe el dia, escribe este ajuste. */
    private int diasAjuste;

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
                now, ZONA_POR_DEFECTO, false, 0, now, now, null, null, null, null, 0);
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
                clock.today().plusDays(1), null, ZONA_POR_DEFECTO, false, 0, now, now, null, null, null, null, 0);
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

    /** Firma intermedia (15 campos, sin `diaProgramaAvanzadoEl`): se conserva por el
     * mismo motivo que la de 12 campos — no obligar a los llamadores existentes
     * (varios tests) a agregar un campo que no rehidratan. */
    public static ParticipacionPrograma rehydrate(UserId participanteId, UserId mentorId, UUID celulaId,
                                                   int diaPrograma, FasePrograma fase, LocalDate fechaInicio,
                                                   Instant programaActivadoEn, ZoneId timezone,
                                                   boolean programaCompletado, int diaPostPrograma, Instant creadoEn,
                                                   Instant actualizadoEn, TipoMeta tipoMeta,
                                                   String nombreRetoPersonal, Instant programaCompletadoEn) {
        return rehydrate(participanteId, mentorId, celulaId, diaPrograma, fase, fechaInicio, programaActivadoEn,
                timezone, programaCompletado, diaPostPrograma, creadoEn, actualizadoEn, tipoMeta,
                nombreRetoPersonal, programaCompletadoEn, null);
    }

    /** Firma de 16 campos (sin `diasAjuste`): se conserva por el mismo motivo que las
     * anteriores — no obligar a los llamadores existentes a agregar un campo que no
     * rehidratan. Un participante sin ajuste registrado es un participante con ajuste 0. */
    public static ParticipacionPrograma rehydrate(UserId participanteId, UserId mentorId, UUID celulaId,
                                                   int diaPrograma, FasePrograma fase, LocalDate fechaInicio,
                                                   Instant programaActivadoEn, ZoneId timezone,
                                                   boolean programaCompletado, int diaPostPrograma, Instant creadoEn,
                                                   Instant actualizadoEn, TipoMeta tipoMeta,
                                                   String nombreRetoPersonal, Instant programaCompletadoEn,
                                                   LocalDate diaProgramaAvanzadoEl) {
        return rehydrate(participanteId, mentorId, celulaId, diaPrograma, fase, fechaInicio, programaActivadoEn,
                timezone, programaCompletado, diaPostPrograma, creadoEn, actualizadoEn, tipoMeta,
                nombreRetoPersonal, programaCompletadoEn, diaProgramaAvanzadoEl, 0);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static ParticipacionPrograma rehydrate(UserId participanteId, UserId mentorId, UUID celulaId,
                                                   int diaPrograma, FasePrograma fase, LocalDate fechaInicio,
                                                   Instant programaActivadoEn, ZoneId timezone,
                                                   boolean programaCompletado, int diaPostPrograma, Instant creadoEn,
                                                   Instant actualizadoEn, TipoMeta tipoMeta,
                                                   String nombreRetoPersonal, Instant programaCompletadoEn,
                                                   LocalDate diaProgramaAvanzadoEl, int diasAjuste) {
        return new ParticipacionPrograma(participanteId, mentorId, celulaId, diaPrograma, fase, fechaInicio,
                programaActivadoEn, timezone, programaCompletado, diaPostPrograma, creadoEn, actualizadoEn,
                tipoMeta, nombreRetoPersonal, programaCompletadoEn, diaProgramaAvanzadoEl, diasAjuste);
    }

    /**
     * `fecha_inicio + 90 + dias_ajuste_programa`. Corre hacia adelante cuando a un
     * participante se le devolvieron dias (V20): si viajo una semana y se lo retrocedio,
     * sus 90 dias de programa terminan una semana mas tarde — que es justamente el punto
     * de retrocederlo. NO coincide con la columna generada `fecha_graduacion_esperada`
     * del baseline, que ignora el ajuste; manda este metodo.
     */
    public LocalDate fechaGraduacionEsperada() {
        return fechaInicio.plusDays(DURACION_PROGRAMA_DIAS + (long) diasAjuste);
    }

    /**
     * <b>La cuenta que define el reloj del programa</b> (V20). Dia 1 es
     * {@link #fechaInicio}; de ahi en adelante son dias de calendario EN LA ZONA DEL
     * PARTICIPANTE, menos los que no cuentan ({@link #diasAjuste}), acotado a [0, 90].
     *
     * <p>Devuelve 0 mientras el reloj no arranco: nadie eligio su Dia 1
     * ({@link #estaActivado()} falso) o la fecha elegida todavia no llego.
     *
     * <p>Es una funcion pura de (fechaInicio, hoy, diasAjuste) — no mira el estado
     * actual de {@link #diaPrograma}. Esa es toda la diferencia con el modelo viejo, que
     * incrementaba: una cuenta no se puede "atrasar" por una corrida que no ocurrio.
     */
    public int diaProgramaDerivado(LocalDate hoyEnZonaParticipante) {
        Objects.requireNonNull(hoyEnZonaParticipante, "hoyEnZonaParticipante es obligatorio");
        if (!estaActivado() || fechaInicio.isAfter(hoyEnZonaParticipante)) {
            return 0;
        }
        long transcurridos = ChronoUnit.DAYS.between(fechaInicio, hoyEnZonaParticipante) + 1;
        return (int) Math.clamp(transcurridos - diasAjuste, 0, DURACION_PROGRAMA_DIAS);
    }

    /**
     * Ajuste operativo de un ADMIN/ALCHEMIST (panel admin de aprendices, gap #7 de
     * docs/PLAN_INTEGRACION_FRONTEND.md): "este aprendiz viajo dos semanas, devolvelo al
     * dia 34". El limite [0, 90] es la misma invariante de siempre.
     *
     * <p><b>Que cambio en V20:</b> ya no escribe {@link #diaPrograma} a mano — eso era
     * incoherente con el modelo derivado (la proxima sincronizacion lo hubiera pisado, y
     * ademas dejaba {@link #fechaInicio} contando una historia distinta a la del dia).
     * Escribe {@link #diasAjuste}, el corrimiento que hace que HOY caiga en
     * {@code nuevoDia}, y despues materializa el dia. Efectos que salen gratis de
     * hacerlo asi: el ajuste PERSISTE (manana el reloj sigue desde el dia 35, no vuelve
     * de un salto al dia real), la graduacion se corre sola, y el ajuste queda registrado
     * y auditable en vez de perderse dentro de un contador.
     *
     * <p>Retroceder NO borra nada: los habitos, evidencias y puntajes ya ganados quedan
     * como estan (viven en otras tablas, con su propia fecha). Se retoma el conteo, no se
     * reescribe la historia.
     */
    public void fijarDia(int nuevoDia, Clock clock) {
        if (nuevoDia < 0 || nuevoDia > DURACION_PROGRAMA_DIAS) {
            throw new IllegalArgumentException(
                    "diaPrograma debe estar entre 0 y " + DURACION_PROGRAMA_DIAS + ", recibido: " + nuevoDia);
        }
        LocalDate hoy = hoyEnMiZona(clock);
        if (estaActivado() && !fechaInicio.isAfter(hoy)) {
            long transcurridos = ChronoUnit.DAYS.between(fechaInicio, hoy) + 1;
            this.diasAjuste = (int) (transcurridos - nuevoDia);
        }
        this.diaPrograma = nuevoDia;
        // D-67: la fase SIEMPRE se deriva del dia, nunca se deja "colgada" del valor
        // anterior — este ajuste manual es justo la via que dejaba una fase vieja
        // conviviendo con un dia nuevo (el bug real de docs/MODULO_PHASECONTRACTS.md §0.2).
        this.fase = FasePrograma.paraDiaPrograma(nuevoDia);
        this.actualizadoEn = clock.now();
    }

    /**
     * Elige el Dia 1 del programa (D-66). <b>Corregido tras revision del dueño del
     * proyecto:</b> la version original de esta regla ofrecia HOY como opcion; se
     * descarto a favor de lo que el backend/frontend viejo ya hacia (`inicioPrograma.ts`,
     * decision de producto 2026-08-10): el reloj del programa avanza a medianoche, asi
     * que firmar Terminos de tarde y elegir "hoy" dejaria un Dia 1 de pocas horas, sin
     * tiempo de completar los habitos de esa jornada. Por eso el rango valido es
     * <b>[hoy+1, hoy+{@value #MAX_DIAS_ESPERA_ACTIVACION}]</b> — MAÑANA es la primera
     * opcion, nunca hoy — siempre en SU zona horaria ({@link #timezone}, nunca UTC ni la
     * del servidor). Como la fecha elegida nunca puede ser hoy, {@code diaPrograma}
     * jamas se adelanta en el acto: siempre queda en 0 hasta que
     * {@link #sincronizarDiaDelPrograma} (barrido del reloj) detecte que llego el dia.
     *
     * <p>Idempotencia (distincion tecnica, no de negocio): reintentar con la MISMA
     * fecha que ya esta activada es un no-op silencioso (200) — un timeout de red no
     * puede convertirse en un error. Reactivar con una fecha DISTINTA es
     * {@link IllegalStateException} (409): eso si reescribiria la historia del
     * programa.
     */
    public void activarPrograma(LocalDate fechaElegida, Clock clock) {
        Objects.requireNonNull(fechaElegida, "fechaElegida es obligatoria");
        if (estaActivado()) {
            if (fechaElegida.equals(fechaInicio)) {
                return; // mismo pedido de nuevo (ej. reintento de red): no-op
            }
            throw new IllegalStateException(
                    "El programa ya fue activado el " + programaActivadoEn + ", no se puede reactivar");
        }
        LocalDate hoy = hoyEnMiZona(clock);
        LocalDate primeraOpcion = hoy.plusDays(1);
        LocalDate limite = hoy.plusDays(MAX_DIAS_ESPERA_ACTIVACION);
        if (fechaElegida.isBefore(primeraOpcion) || fechaElegida.isAfter(limite)) {
            throw new IllegalArgumentException("La fecha de inicio debe estar entre " + primeraOpcion + " y "
                    + limite + " (tu zona: " + timezone + "), recibida: " + fechaElegida);
        }
        this.fechaInicio = fechaElegida;
        this.programaActivadoEn = clock.now();
        this.actualizadoEn = clock.now();
    }

    /**
     * Las {@value #MAX_DIAS_ESPERA_ACTIVACION} fechas validas para {@link #activarPrograma}
     * (mañana, +2, +3 en la zona del participante — NUNCA hoy) — para que la app sepa
     * que botones ofrecer sin adivinar la aritmetica del servidor (mismo criterio
     * documentado en `inicioPrograma.ts` del cliente movil: "quien manda es el
     * servidor").
     */
    public List<LocalDate> opcionesDeActivacion(Clock clock) {
        LocalDate hoy = hoyEnMiZona(clock);
        return List.of(hoy.plusDays(1), hoy.plusDays(2), hoy.plusDays(3));
    }

    /**
     * Materializa {@link #diaProgramaDerivado(LocalDate)} en el agregado — lo que corre
     * el barrido del reloj (V20; antes {@code avanzarDiaDelPrograma}, incremental).
     * {@code hoyEnZonaParticipante} lo calcula el llamador
     * ({@code clock.now().atZone(timezone).toLocalDate()}) porque es el mismo dato que
     * {@link #diaProgramaAvanzadoEl} — pasarlo en vez de recalcularlo aca adentro evita
     * que dominio y aplicacion puedan discrepar en la zona usada.
     *
     * <p>Devuelve {@code true} SOLO si algo cambio, para que el barrido guarde unicamente
     * las filas que lo necesitan: el reloj corre cada hora (ver
     * {@code AvanzarDiaProgramaScheduler}, que debe alcanzar la medianoche local de
     * cualquier zona) y sin este filtro serian 24 UPDATE diarios por participante.
     *
     * <p>No toca nada mientras el reloj no arranco: nadie eligio su Dia 1
     * ({@link #estaActivado()} falso) o la fecha elegida todavia no llego. Ese caso se
     * distingue a proposito de "derivado = 0": un participante pre-activacion conserva
     * el dia que un admin le haya fijado a mano.
     */
    public boolean sincronizarDiaDelPrograma(LocalDate hoyEnZonaParticipante, Clock clock) {
        Objects.requireNonNull(hoyEnZonaParticipante, "hoyEnZonaParticipante es obligatorio");
        if (!estaActivado() || fechaInicio.isAfter(hoyEnZonaParticipante)) {
            return false;
        }
        int derivado = diaProgramaDerivado(hoyEnZonaParticipante);
        if (derivado == diaPrograma && hoyEnZonaParticipante.equals(diaProgramaAvanzadoEl)) {
            return false;
        }
        this.diaPrograma = derivado;
        this.diaProgramaAvanzadoEl = hoyEnZonaParticipante;
        this.fase = FasePrograma.paraDiaPrograma(derivado);
        this.actualizadoEn = clock.now();
        return true;
    }

    private LocalDate hoyEnMiZona(Clock clock) {
        return clock.now().atZone(timezone).toLocalDate();
    }

    public void asignarMentor(UserId nuevoMentorId, Clock clock) {
        this.mentorId = Objects.requireNonNull(nuevoMentorId, "mentorId es obligatorio");
        this.actualizadoEn = clock.now();
    }

    /**
     * Panel admin de celulas (gap #25, docs/PLAN_INTEGRACION_FRONTEND.md §5). `users` es
     * dueño de la columna `participantes_programa.celula_id`, pero NO de la existencia de
     * la celula misma (eso vive en `community.domain.model.celula.Celula`, otro modulo) —
     * por eso este metodo no valida el UUID contra nada, solo escribe el valor que el
     * llamador (que ya validó la celula en su propio modulo) le pasa. Mismo criterio de
     * dos metodos separados (asignar/quitar) que {@code Celula.asignarMentor}/{@code
     * quitarMentor} en `community`.
     */
    public void asignarCelula(UUID nuevaCelulaId, Clock clock) {
        this.celulaId = Objects.requireNonNull(nuevaCelulaId, "celulaId es obligatorio");
        this.actualizadoEn = clock.now();
    }

    /** Contraparte de {@link #asignarCelula} — saca al participante de su celula actual. */
    public void quitarCelula(Clock clock) {
        this.celulaId = null;
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
        return "ParticipacionPrograma[" + participanteId + ", dia=" + diaPrograma + ", ajuste=" + diasAjuste
                + ", " + fase + "]";
    }
}
