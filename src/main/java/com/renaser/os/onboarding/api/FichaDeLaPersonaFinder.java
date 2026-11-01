package com.renaser.os.onboarding.api;

import com.renaser.os.shared.domain.UserId;

/**
 * Qué ficha le toca a una persona en su ingreso (spec 026): la estándar de siempre o el diagnóstico. La decide
 * Administración al aceptar su solicitud (spec 025, por {@code users.api.FichaAlAceptar}) o después, a quien ya está dentro
 * ({@link FichaDeLaPersonaUseCase}).
 */
public interface FichaDeLaPersonaFinder {

    /** {@link TipoDeFicha#ESTANDAR} también para quien nunca abrió su onboarding (sin fila). Nunca falla por eso. */
    TipoDeFicha tipoDe(UserId persona);

    enum TipoDeFicha {
        ESTANDAR,
        CON_DIAGNOSTICO
    }
}
