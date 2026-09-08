package com.renaser.os.onboarding.application.ports.out.mapa;

import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * La marca de etapa completada, POR FLUJO (`etapas_onboarding_completadas`, V41).
 *
 * <p>No se usa `estado_onboarding.completado` porque es UNA fila por usuario con UN flujo actual, y
 * los seis flujos la comparten: marcar ahi mezclaria "termino el Dia 0" con "termino el mapa". Es
 * el pendiente que docs/PENDIENTES_2026-09-05.md §3.5 pedia resolver una sola vez, porque las
 * etapas 3, 4 y 5 van a necesitar lo mismo.
 */
public interface EtapaOnboardingPort {

    Set<String> flujosCompletados(UserId participanteId);

    /** Idempotente: marcar dos veces la misma etapa no cambia la fecha original. */
    void marcarCompletada(UserId participanteId, String flujo);
}
