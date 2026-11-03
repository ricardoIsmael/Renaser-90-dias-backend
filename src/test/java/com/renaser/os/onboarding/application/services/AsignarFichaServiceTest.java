package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.FichaDeLaPersonaUseCase;
import com.renaser.os.onboarding.application.ports.in.diagnostico.AsignarFichaUseCase.ResultadoDeAsignacion;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.domain.model.estado.TipoDeFicha;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Darle el diagnóstico a quienes ya están dentro (026-9 B): una persona que no se puede no frena a las demás. */
class AsignarFichaServiceTest {

    private static final UserId ADMIN = UserId.of(UUID.randomUUID());
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId LUIS = UserId.of(UUID.randomUUID());

    private FichaDeLaPersonaUseCase ficha;
    private AsignarFichaService service;

    @BeforeEach
    void preparar() {
        ConsultarActorPort actores = mock(ConsultarActorPort.class);
        when(actores.deActor(ADMIN)).thenReturn(Optional.of(new ActorOnboarding(ADMIN, false, "Admin", true)));
        when(actores.deActor(MENTOR)).thenReturn(Optional.of(new ActorOnboarding(MENTOR, false, "Mentor", false)));
        when(actores.deActor(LUIS)).thenReturn(Optional.of(new ActorOnboarding(LUIS, false, "Luis", false)));
        ficha = mock(FichaDeLaPersonaUseCase.class);
        service = new AsignarFichaService(new GuardiaDelDiagnostico(actores), ficha, actores);
    }

    @Test
    @DisplayName("asigna a todas las que puede y deja como omitidas, con su motivo, las que no (ya respondieron, ya terminaron)")
    void asignaYOmite() {
        doThrow(new IllegalStateException("Ya respondió su ficha: no se le cambia")).when(ficha).asignarDiagnostico(LUIS, ADMIN);

        ResultadoDeAsignacion resultado = service.asignar(ADMIN, List.of(ANA, LUIS, ANA), TipoDeFicha.CON_DIAGNOSTICO);

        assertThat(resultado.asignadas()).isEqualTo(1);
        assertThat(resultado.omitidas()).singleElement().satisfies(o -> {
            assertThat(o.id()).isEqualTo(LUIS);
            assertThat(o.nombre()).isEqualTo("Luis");
            assertThat(o.motivo()).contains("Ya respondió");
        });
        verify(ficha).asignarDiagnostico(ANA, ADMIN);
    }

    @Test
    @DisplayName("devolver a la ficha estándar usa el otro camino")
    void devolverALaEstandar() {
        ResultadoDeAsignacion resultado = service.asignar(ADMIN, List.of(ANA), TipoDeFicha.ESTANDAR);

        assertThat(resultado.asignadas()).isEqualTo(1);
        verify(ficha).quitarDiagnostico(ANA, ADMIN);
        verify(ficha, never()).asignarDiagnostico(any(), any());
    }

    @Test
    @DisplayName("sin personas se rechaza; un mentor recibe 403 y no se toca a nadie")
    void guardas() {
        assertThatThrownBy(() -> service.asignar(ADMIN, List.of(), TipoDeFicha.CON_DIAGNOSTICO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.asignar(MENTOR, List.of(ANA), TipoDeFicha.CON_DIAGNOSTICO)).isInstanceOf(NotAuthorizedException.class);
        verify(ficha, never()).asignarDiagnostico(any(), any());
    }
}
