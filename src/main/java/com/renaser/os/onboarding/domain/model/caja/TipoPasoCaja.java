package com.renaser.os.onboarding.domain.model.caja;

import java.util.Optional;

/**
 * Lo que se GUARDA de la caja: cada paso es una fila de {@code etapas_onboarding_completadas} con
 * {@code flujo = 'caja:<envío>:<paso>'} (V82). El nombre es parte del dato: renombrar uno obliga a migrar.
 *
 * <p>El orden de declaración desempata dos pasos marcados en el mismo instante: dentro de un envío, siempre
 * va armando, después enviada, después entregada o con problema.
 */
public enum TipoPasoCaja {

    /** El Admin la aprobó caso por caso: pasa a {@link EstadoCaja#POR_REVISAR} sin cumplir el requisito. */
    APROBADA(EstadoCaja.POR_REVISAR),
    ARMANDO(EstadoCaja.ARMANDO),
    /** La foto de la caja armada (se puede cambiar mientras se arma: la fila se reemplaza). */
    FOTO(null),
    /** El comprobante del envío (idem). */
    COMPROBANTE(null),
    ENVIADA(EstadoCaja.ENVIADA),
    ENTREGADA(EstadoCaja.ENTREGADA),
    CON_PROBLEMA(EstadoCaja.CON_PROBLEMA),
    /** Marca de «ya se avisó que está en revisión»: hace idempotente el barrido horario. */
    AVISO_EN_REVISION(null),
    /** Marca del recordatorio al aprendiz a los 3 días de enviada. */
    AVISO_RECORDATORIO(null),
    /** Marca del aviso al Admin a los 5 días de enviada sin confirmar. */
    AVISO_SIN_CONFIRMAR(null);

    private final EstadoCaja estado;

    TipoPasoCaja(EstadoCaja estado) {
        this.estado = estado;
    }

    /**
     * El estado al que lleva la caja, si es un paso de estado del envío (armando, enviada, entregada, con
     * problema). La aprobación no: es previa a cualquier envío, y las fotos y los avisos no mueven nada.
     */
    public Optional<EstadoCaja> estadoDelEnvio() {
        return this == APROBADA ? Optional.empty() : Optional.ofNullable(estado);
    }

    /** Si aparece en el historial que ve el Admin (con el estado al que llevó). */
    public Optional<EstadoCaja> estadoDelHistorial() {
        return Optional.ofNullable(estado);
    }
}
