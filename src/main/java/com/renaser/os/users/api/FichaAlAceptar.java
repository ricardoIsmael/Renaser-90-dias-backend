package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Qué ficha completa la persona que se acaba de aceptar: la estándar o el diagnóstico (spec 025 + spec 026).
 *
 * <p><b>Lo implementa {@code onboarding}</b> (dueño de la ficha y de la marca {@code estado_onboarding.ficha}) y lo
 * consume {@code users} al aceptar una solicitud, dentro de la misma transacción: la dependencia va de {@code onboarding}
 * hacia {@code users}, como siempre, y {@code users} no importa a nadie (mismo patrón que {@link GruposDelPersonalFinder}).
 * No llamarlo deja a la persona con la ficha estándar, que es lo que pasa hoy.
 */
public interface FichaAlAceptar {

    /** La persona entra «con diagnóstico»: en su primer ingreso ve el diagnóstico en lugar de la ficha estándar. */
    void conDiagnostico(UserId persona, UserId quien);
}
