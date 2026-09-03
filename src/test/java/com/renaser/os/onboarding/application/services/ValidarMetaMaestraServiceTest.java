package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase.ValidarMetaMaestraCommand;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Con el veredicto de IA fuera del alcance (2026-09-03), lo unico que este servicio decide es
 * si el actor puede mandar su meta. El texto ya no se juzga, asi que no hay caso de aprobada
 * ni de rechazada que probar.
 */
@ExtendWith(MockitoExtension.class)
class ValidarMetaMaestraServiceTest {

    @Mock
    private ConsultarActorPort actorPort;

    private ValidarMetaMaestraService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new ValidarMetaMaestraService(actorPort);
        actorId = UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("actor suspendido -> NotAuthorizedException")
    void actorSuspendidoRechazado() {
        when(actorPort.deActor(actorId)).thenReturn(Optional.of(new ActorOnboarding(actorId, true)));

        var command = new ValidarMetaMaestraCommand(actorId, "meta maestra");

        assertThatThrownBy(() -> service.aceptar(command)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("actor inexistente -> NoSuchElementException")
    void actorInexistente() {
        when(actorPort.deActor(actorId)).thenReturn(Optional.empty());

        var command = new ValidarMetaMaestraCommand(actorId, "meta maestra");

        assertThatThrownBy(() -> service.aceptar(command)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("actor activo -> la meta pasa, sin que nada la evalue")
    void actorActivoPasa() {
        when(actorPort.deActor(actorId)).thenReturn(Optional.of(new ActorOnboarding(actorId, false)));

        var command = new ValidarMetaMaestraCommand(actorId, "cualquier texto, nadie lo juzga");

        assertThatCode(() -> service.aceptar(command)).doesNotThrowAnyException();
    }
}
