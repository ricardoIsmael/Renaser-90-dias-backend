package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Qué ficha de entrada le toca a la persona que se acepta (spec 025, D-302). <b>Lo implementa {@code onboarding}</b> (spec 026: dueño de la ficha y de la
 * marca «con o sin diagnóstico», que vive en su {@code estado_onboarding}) y lo consume {@code users} al aceptar una solicitud: la dependencia va de
 * {@code onboarding} hacia {@code users}, como siempre, y {@code users} no importa a nadie (el mismo patrón de {@link GruposDelPersonalFinder}).
 *
 * <p>Se invoca DENTRO de la transacción de aceptar: si falla, la aceptación entera se deshace (FR-008). Sin diagnóstico no se llama a nada: la ficha
 * estándar es lo de siempre y no necesita ninguna marca.
 */
public interface FichaAlAceptar {

    /** ¿Hay un diagnóstico publicado que asignar? Si no, «Con diagnóstico» no se ofrece al aceptar (spec 026, FR-004). */
    boolean hayDiagnosticoPublicado();

    /** La persona entra con el diagnóstico en lugar de la ficha estándar. {@code quien} es Administración, para el historial. */
    void conDiagnostico(UserId persona, UserId quien);
}
