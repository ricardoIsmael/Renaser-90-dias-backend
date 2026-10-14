package com.renaser.os.plataforma.application.services;

import com.renaser.os.community.api.GrupoDePruebaUseCase;
import com.renaser.os.onboarding.api.OnboardingDeCuentaDePruebaUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentaDePrueba;
import com.renaser.os.users.api.CuentasDePruebaUseCase;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lo que una cuenta de prueba necesita alrededor para que «ver como» se parezca a la app real (contrato fase-2.md §5;
 * D-270): el aprendiz, la guía y el mentor de prueba viven en el «Grupo de prueba (ver como)», que el mentor lidera, y el
 * aprendiz tiene su onboarding completo (si no, la app le mostraría la ficha inicial). Los roles sin grupo (líder,
 * admin, alquimista) no necesitan nada más.
 *
 * <p>Una cuenta de un rol personalizado entra al mismo grupo: con base aprendiz, como aprendiz (con su onboarding); con
 * base mentor o guía, como guía (un grupo tiene un solo mentor).
 */
@Component
class EntornoDeLaCuentaDePrueba {

    private static final Set<UserRole> ROLES_CON_GRUPO = Set.of(UserRole.TRAINEE, UserRole.TRAINEE_GUIDE,
            UserRole.MENTOR);

    private final CuentasDePruebaUseCase cuentas;
    private final GrupoDePruebaUseCase grupo;
    private final OnboardingDeCuentaDePruebaUseCase onboarding;

    EntornoDeLaCuentaDePrueba(CuentasDePruebaUseCase cuentas, GrupoDePruebaUseCase grupo,
                              OnboardingDeCuentaDePruebaUseCase onboarding) {
        this.cuentas = cuentas;
        this.grupo = grupo;
        this.onboarding = onboarding;
    }

    void preparar(UserId soporteTi, CuentaDePrueba cuenta) {
        if (!ROLES_CON_GRUPO.contains(cuenta.rol())) {
            return;
        }
        CuentaDePrueba mentor = cuentas.deRol(soporteTi, UserRole.MENTOR);
        CuentaDePrueba aprendiz = cuentas.deRol(soporteTi, UserRole.TRAINEE);
        CuentaDePrueba guia = cuentas.deRol(soporteTi, UserRole.TRAINEE_GUIDE);
        Set<UserId> aprendices = new LinkedHashSet<>(Set.of(aprendiz.id()));
        Set<UserId> guias = new LinkedHashSet<>(Set.of(guia.id()));
        if (cuenta.rolPersonalizado() != null) {
            (cuenta.rol() == UserRole.TRAINEE ? aprendices : guias).add(cuenta.id());
        }
        for (UserId unAprendiz : aprendices) {
            onboarding.completar(soporteTi, unAprendiz);
        }
        grupo.asegurar(soporteTi, mentor.id(), aprendices, guias);
    }
}
