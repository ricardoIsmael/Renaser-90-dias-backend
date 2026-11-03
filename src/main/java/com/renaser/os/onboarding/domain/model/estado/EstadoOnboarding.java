package com.renaser.os.onboarding.domain.model.estado;

import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.onboarding.domain.model.pagos.PagoDeLaPersona;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.Accessors;

import java.time.Instant;
import java.util.Objects;

/**
 * Agregado raiz 1:1 con {@code usuarios} (tabla {@code estado_onboarding}, PK =
 * {@code usuario_id}). Guarda en que paso de la UI esta el aprendiz (para reanudar) y los
 * hitos de aceptacion/firma del Pacto de Fase I.
 *
 * <p>{@code progresoFlujo} es JSON crudo, tratado como dato OPACO (CLAUDE.MD, decision de
 * este modulo): es estado de reanudacion de UI que el cliente movil arma y vuelve a leer
 * tal cual, el backend no lo interpreta ni lo valida.
 */
@Getter
@Accessors(fluent = true)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@EqualsAndHashCode(of = "usuarioId")
public final class EstadoOnboarding {

    private final UserId usuarioId;
    private String flujoActual;
    private String seccionActual;
    private Integer pasoActual;
    private String progresoFlujo;
    private Instant terminosAceptadosEn;
    private Instant pactoAceptadoEn;
    private Instant pactoFirmadoEn;
    private Instant rocasSyncAceptadoEn;
    private Instant iniciadoEn;
    private Instant ultimaActividadEn;
    private boolean completado;
    private Instant completadoEn;
    private final Instant creadoEn;
    private Instant actualizadoEn;
    private TipoDeFicha ficha;
    private UserId fichaAsignadaPor;
    private Instant fichaAsignadaEn;
    private Integer fichaVersion;
    /** Lo que tiene anotado de su pago (spec 032): modalidad, moneda y confirmación del cronograma. Las cuotas son filas aparte. */
    private PagoDeLaPersona pago;

    /** Primera vez que el aprendiz abre el onboarding: fila nueva, todo vacio salvo timestamps. */
    public static EstadoOnboarding iniciar(UserId usuarioId, Clock clock) {
        Instant ahora = clock.now();
        return new EstadoOnboarding(requireUsuarioId(usuarioId), null, null, null, null,
                null, null, null, null, ahora, ahora, false, null, ahora, ahora,
                TipoDeFicha.ESTANDAR, null, null, null, PagoDeLaPersona.sinAnotar());
    }

    /**
     * Fila que nace porque Administración le anotó su pago a alguien que todavía no abrió su onboarding (spec 032): la persona aún no empezó, así que
     * {@code iniciadoEn} queda vacío hasta que lo abra ({@link #registrarInicio}). Con la ficha estándar, que es la de siempre.
     */
    public static EstadoOnboarding sinEmpezar(UserId usuarioId, Clock clock) {
        Instant ahora = clock.now();
        return new EstadoOnboarding(requireUsuarioId(usuarioId), null, null, null, null,
                null, null, null, null, null, null, false, null, ahora, ahora,
                TipoDeFicha.ESTANDAR, null, null, null, PagoDeLaPersona.sinAnotar());
    }

    /**
     * Fila que nace porque Administración le asignó el diagnóstico a alguien que todavía no abrió su onboarding (spec
     * 026): la persona aún no empezó, así que {@code iniciadoEn} queda vacío hasta que lo abra ({@link #registrarInicio}).
     */
    public static EstadoOnboarding conDiagnostico(UserId usuarioId, UserId quien, Clock clock) {
        Instant ahora = clock.now();
        return new EstadoOnboarding(requireUsuarioId(usuarioId), null, null, null, null,
                null, null, null, null, null, null, false, null, ahora, ahora,
                TipoDeFicha.CON_DIAGNOSTICO, quien, ahora, null, PagoDeLaPersona.sinAnotar());
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static EstadoOnboarding rehydrate(UserId usuarioId, String flujoActual, String seccionActual,
                                              Integer pasoActual, String progresoFlujo, Instant terminosAceptadosEn,
                                              Instant pactoAceptadoEn, Instant pactoFirmadoEn,
                                              Instant rocasSyncAceptadoEn, Instant iniciadoEn,
                                              Instant ultimaActividadEn, boolean completado, Instant completadoEn,
                                              Instant creadoEn, Instant actualizadoEn, TipoDeFicha ficha,
                                              UserId fichaAsignadaPor, Instant fichaAsignadaEn,
                                              Integer fichaVersion, PagoDeLaPersona pago) {
        return new EstadoOnboarding(usuarioId, flujoActual, seccionActual, pasoActual, progresoFlujo,
                terminosAceptadosEn, pactoAceptadoEn, pactoFirmadoEn, rocasSyncAceptadoEn, iniciadoEn,
                ultimaActividadEn, completado, completadoEn, creadoEn, actualizadoEn, ficha, fichaAsignadaPor,
                fichaAsignadaEn, fichaVersion, pago);
    }

    /** La persona abre su onboarding por primera vez: si la fila nació por una asignación, ahora sí empezó. */
    public void registrarInicio(Clock clock) {
        if (this.iniciadoEn != null) {
            return;
        }
        Instant ahora = clock.now();
        this.iniciadoEn = ahora;
        this.ultimaActividadEn = ahora;
        this.actualizadoEn = ahora;
    }

    /** {@code true} si le toca el diagnóstico en lugar de la ficha estándar. */
    public boolean conDiagnostico() {
        return ficha == TipoDeFicha.CON_DIAGNOSTICO;
    }

    /**
     * Le asigna el diagnóstico (spec 026). Idempotente: si ya lo tenía no cambia quién ni cuándo se lo asignó. Que la
     * persona no haya terminado ya su diagnóstico lo decide el servicio, que es quien sabe de las etapas.
     */
    public void asignarDiagnostico(UserId quien, Clock clock) {
        if (conDiagnostico()) {
            return;
        }
        Instant ahora = clock.now();
        this.ficha = TipoDeFicha.CON_DIAGNOSTICO;
        this.fichaAsignadaPor = quien;
        this.fichaAsignadaEn = ahora;
        this.fichaVersion = null;
        this.actualizadoEn = ahora;
    }

    /**
     * Vuelve a la ficha estándar. Solo se puede si la persona todavía no respondió nada del diagnóstico: con respuestas
     * se mezclarían las de dos fichas (025-Q5, 026-9). El servicio es quien sabe si respondió.
     */
    public void quitarDiagnostico(boolean yaRespondio, Clock clock) {
        if (!conDiagnostico()) {
            return;
        }
        if (yaRespondio) {
            throw new IllegalStateException(
                    "Esta persona ya respondió preguntas del diagnóstico: no se puede volver a la ficha estándar");
        }
        this.ficha = TipoDeFicha.ESTANDAR;
        this.fichaAsignadaPor = null;
        this.fichaAsignadaEn = null;
        this.fichaVersion = null;
        this.actualizadoEn = clock.now();
    }

    /** Fija la versión publicada con la que empezó el diagnóstico; después ya no cambia (FR-031). */
    public void fijarVersionDelDiagnostico(int version, Clock clock) {
        if (fichaVersion != null) {
            return;
        }
        this.fichaVersion = version;
        this.actualizadoEn = clock.now();
    }

    /**
     * Anota que la persona pagó completo (spec 032). Quita lo que hubiera de un cronograma: las cuotas las borra el servicio, que es quien sabe si hay alguna pagada.
     */
    public void anotarSinCronograma(Clock clock) {
        this.pago = PagoDeLaPersona.sinCronograma();
        this.actualizadoEn = clock.now();
    }

    /** Anota que la persona paga en cuotas, en esa moneda. Parte sin confirmar: la confirmación anterior, si la hubo, era de otro cronograma. */
    public void anotarConCronograma(Moneda moneda, Clock clock) {
        this.pago = PagoDeLaPersona.conCronograma(moneda);
        this.actualizadoEn = clock.now();
    }

    /** La persona confirma su cronograma: queda con su nombre, la hora y la huella de lo que confirmó (ver {@link PagoDeLaPersona#confirmado}). */
    public void confirmarCronograma(String nombre, String huella, Clock clock) {
        Instant ahora = clock.now();
        this.pago = pago.confirmado(nombre, huella, ahora);
        this.actualizadoEn = ahora;
    }

    /**
     * Mueve el cursor de la UI (flujo/seccion/paso) y guarda el estado de reanudacion.
     * Cualquier parametro {@code null} deja el campo existente sin tocar (avance parcial:
     * el cliente puede mandar solo lo que cambio).
     */
    public void avanzar(String flujo, String seccion, Integer paso, String progresoFlujoJson, Clock clock) {
        if (flujo != null) {
            this.flujoActual = flujo;
        }
        if (seccion != null) {
            this.seccionActual = seccion;
        }
        if (paso != null) {
            this.pasoActual = paso;
        }
        if (progresoFlujoJson != null) {
            this.progresoFlujo = progresoFlujoJson;
        }
        Instant ahora = clock.now();
        this.ultimaActividadEn = ahora;
        this.actualizadoEn = ahora;
    }

    /**
     * Marca un hito como aceptado (o re-aceptado: cada llamada actualiza el timestamp al
     * momento actual — decision de este modulo, ver docs/MODULO_ONBOARDING.md: no hay
     * confirmacion de que "aceptar" deba ser irreversible o de una sola vez).
     */
    public void aceptarHito(HitoOnboarding hito, Clock clock) {
        Objects.requireNonNull(hito, "hito es obligatorio");
        Instant ahora = clock.now();
        switch (hito) {
            case TERMINOS -> this.terminosAceptadosEn = ahora;
            case PACTO -> this.pactoAceptadoEn = ahora;
            case PACTO_FIRMADO -> this.pactoFirmadoEn = ahora;
            case ROCAS_SYNC -> this.rocasSyncAceptadoEn = ahora;
        }
        this.ultimaActividadEn = ahora;
        this.actualizadoEn = ahora;
    }

    /**
     * Marca el onboarding como completo. Accion EXPLICITA invocada por quien llama
     * (CompletarOnboardingUseCase) — NO hay regla automatica de "cuando esta completo"
     * (ninguna fue confirmada, CLAUDE.MD §0.6). Idempotente: completar dos veces conserva
     * el {@code completadoEn} original.
     */
    public void marcarCompletado(Clock clock) {
        if (this.completado) {
            return;
        }
        Instant ahora = clock.now();
        this.completado = true;
        this.completadoEn = ahora;
        this.ultimaActividadEn = ahora;
        this.actualizadoEn = ahora;
    }

    private static UserId requireUsuarioId(UserId usuarioId) {
        return Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
    }

    @Override
    public String toString() {
        return "EstadoOnboarding[" + usuarioId + ", flujo=" + flujoActual + ", completado=" + completado + "]";
    }
}
