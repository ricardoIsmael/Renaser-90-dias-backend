package com.renaser.os.plataforma.application.services;

import com.renaser.os.community.api.GrupoDePruebaUseCase;
import com.renaser.os.onboarding.api.OnboardingDeCuentaDePruebaUseCase;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentaDePrueba;
import com.renaser.os.users.api.CuentasDePruebaUseCase;
import com.renaser.os.users.api.RolPersonalizadoAsignado;
import com.renaser.os.users.api.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** El entorno de cada cuenta de prueba: grupo, onboarding (D-270). Sin Spring. */
@ExtendWith(MockitoExtension.class)
class EntornoDeLaCuentaDePruebaTest {

    @Mock
    private CuentasDePruebaUseCase cuentas;
    @Mock
    private GrupoDePruebaUseCase grupo;
    @Mock
    private OnboardingDeCuentaDePruebaUseCase onboarding;

    private EntornoDeLaCuentaDePrueba entorno;
    private final UserId soporte = UserId.of(UUID.randomUUID());
    private final CuentaDePrueba mentor = cuenta(UserRole.MENTOR, null);
    private final CuentaDePrueba aprendiz = cuenta(UserRole.TRAINEE, null);
    private final CuentaDePrueba guia = cuenta(UserRole.TRAINEE_GUIDE, null);

    private static CuentaDePrueba cuenta(UserRole rol, RolPersonalizadoAsignado personalizado) {
        return new CuentaDePrueba(UserId.of(UUID.randomUUID()), "Prueba", rol, personalizado);
    }

    @BeforeEach
    void armar() {
        entorno = new EntornoDeLaCuentaDePrueba(cuentas, grupo, onboarding);
        lenient().when(cuentas.deRol(soporte, UserRole.MENTOR)).thenReturn(mentor);
        lenient().when(cuentas.deRol(soporte, UserRole.TRAINEE)).thenReturn(aprendiz);
        lenient().when(cuentas.deRol(soporte, UserRole.TRAINEE_GUIDE)).thenReturn(guia);
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"TRAINEE", "TRAINEE_GUIDE", "MENTOR"})
    @DisplayName("aprendiz, guía y mentor: el grupo de prueba con los tres, y el onboarding del aprendiz completo")
    void conGrupo(UserRole rol) {
        entorno.preparar(soporte, cuenta(rol, null));

        verify(onboarding).completar(soporte, aprendiz.id());
        verify(grupo).asegurar(soporte, mentor.id(), Set.of(aprendiz.id()), Set.of(guia.id()));
    }

    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR_LEAD", "ADMIN", "ALCHEMIST"})
    @DisplayName("líder, admin y alquimista no necesitan grupo")
    void sinGrupo(UserRole rol) {
        entorno.preparar(soporte, cuenta(rol, null));

        verifyNoInteractions(cuentas, grupo, onboarding);
    }

    @Test
    @DisplayName("un rol personalizado de base aprendiz entra como aprendiz, con su onboarding; uno de base mentor, como guía")
    void rolesPersonalizados() {
        CuentaDePrueba aprendizJunior = cuenta(UserRole.TRAINEE, new RolPersonalizadoAsignado(UUID.randomUUID(), "A"));
        CuentaDePrueba mentorJunior = cuenta(UserRole.MENTOR, new RolPersonalizadoAsignado(UUID.randomUUID(), "M"));

        entorno.preparar(soporte, aprendizJunior);
        verify(onboarding).completar(soporte, aprendizJunior.id());
        verify(grupo).asegurar(soporte, mentor.id(), Set.of(aprendiz.id(), aprendizJunior.id()), Set.of(guia.id()));

        entorno.preparar(soporte, mentorJunior);
        verify(grupo).asegurar(soporte, mentor.id(), Set.of(aprendiz.id()), Set.of(guia.id(), mentorJunior.id()));
    }
}
