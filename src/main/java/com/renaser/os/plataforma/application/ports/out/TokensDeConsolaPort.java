package com.renaser.os.plataforma.application.ports.out;

import com.renaser.os.shared.domain.UserId;

import java.time.Duration;

/**
 * Los tokens propios de la consola Soporte TI (plan.md D6), atados a la persona y a su sesión: el de la consola vence
 * por inactividad y se renueva con cada uso; el de un cambio crítico vence a los pocos minutos y sirve una sola vez.
 */
public interface TokensDeConsolaPort {

    /** Un token nuevo, opaco, que vence en {@code vigencia}. */
    String emitirDeConsola(UserId actor, String sesion, Duration vigencia);

    /** Un token de un solo uso para un cambio crítico. */
    String emitirDeReautenticacion(UserId actor, String sesion, Duration vigencia);

    /** Si el token de la consola sigue vivo y es de esa persona y esa sesión; si lo es, renueva su vigencia. */
    boolean consolaVigente(String token, UserId actor, String sesion, Duration vigencia);

    /** Si el token de reautenticación es válido para esa persona y esa sesión; lo gasta en el acto. */
    boolean gastarReautenticacion(String token, UserId actor, String sesion);
}
