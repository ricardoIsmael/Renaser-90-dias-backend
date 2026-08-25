package com.renaser.os.onboarding.domain.model.estado;

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

    /** Primera vez que el aprendiz abre el onboarding: fila nueva, todo vacio salvo timestamps. */
    public static EstadoOnboarding iniciar(UserId usuarioId, Clock clock) {
        Instant ahora = clock.now();
        return new EstadoOnboarding(requireUsuarioId(usuarioId), null, null, null, null,
                null, null, null, null, ahora, ahora, false, null, ahora, ahora);
    }

    /** Solo para el adaptador de persistencia: reconstruye una fila ya existente. */
    public static EstadoOnboarding rehydrate(UserId usuarioId, String flujoActual, String seccionActual,
                                              Integer pasoActual, String progresoFlujo, Instant terminosAceptadosEn,
                                              Instant pactoAceptadoEn, Instant pactoFirmadoEn,
                                              Instant rocasSyncAceptadoEn, Instant iniciadoEn,
                                              Instant ultimaActividadEn, boolean completado, Instant completadoEn,
                                              Instant creadoEn, Instant actualizadoEn) {
        return new EstadoOnboarding(usuarioId, flujoActual, seccionActual, pasoActual, progresoFlujo,
                terminosAceptadosEn, pactoAceptadoEn, pactoFirmadoEn, rocasSyncAceptadoEn, iniciadoEn,
                ultimaActividadEn, completado, completadoEn, creadoEn, actualizadoEn);
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
