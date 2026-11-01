package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Cambiar la ficha de una persona (spec 026). Quien llama ya autorizó al actor: este puerto solo aplica la regla de la ficha y
 * deja constancia de {@code quien} la cambió.
 */
public interface FichaDeLaPersonaUseCase {

    /**
     * Le asigna el diagnóstico, a quien aún no entró o a quien ya está dentro (026-9). Idempotente. Crea la fila de
     * {@code estado_onboarding} si no existía. {@code 404} si la persona no existe, {@code 403} si está suspendida,
     * {@code 409} si ya terminó su diagnóstico.
     */
    void asignarDiagnostico(UserId persona, UserId quien);

    /**
     * Vuelve a la ficha estándar. Solo si la persona todavía no respondió nada del diagnóstico ({@code 409} si respondió):
     * con respuestas se mezclarían las de dos fichas.
     */
    void quitarDiagnostico(UserId persona, UserId quien);
}
