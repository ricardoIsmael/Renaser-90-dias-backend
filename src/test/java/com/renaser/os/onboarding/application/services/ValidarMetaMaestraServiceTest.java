package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase.ResultadoMetaMaestra.Veredicto;
import com.renaser.os.onboarding.application.ports.in.metamaestra.ValidarMetaMaestraUseCase.ValidarMetaMaestraCommand;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.metamaestra.ValidacionMetaMaestraPort;
import com.renaser.os.onboarding.application.ports.out.metamaestra.ValidacionMetaMaestraPort.ResultadoValidacionMetaMaestra;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ValidarMetaMaestraServiceTest {

    @Mock
    private ValidacionMetaMaestraPort validacionPort;
    @Mock
    private ConsultarActorPort actorPort;

    private ValidarMetaMaestraService service;
    private UserId actorId;

    @BeforeEach
    void setUp() {
        service = new ValidarMetaMaestraService(validacionPort, actorPort);
        actorId = UserId.of(UUID.randomUUID());
    }

    private void actorActivo() {
        when(actorPort.deActor(actorId)).thenReturn(Optional.of(new ActorOnboarding(actorId, false)));
    }

    @Test
    @DisplayName("actor suspendido -> NotAuthorizedException, nunca llama al puerto de IA (mismo criterio que E-33)")
    void actorSuspendidoNuncaLlamaAlPuerto() {
        when(actorPort.deActor(actorId)).thenReturn(Optional.of(new ActorOnboarding(actorId, true)));

        var command = new ValidarMetaMaestraCommand(actorId, "meta maestra");

        assertThatThrownBy(() -> service.validar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(validacionPort, never()).validar(any());
    }

    @Test
    @DisplayName("actor inexistente -> NoSuchElementException")
    void actorInexistente() {
        when(actorPort.deActor(actorId)).thenReturn(Optional.empty());

        var command = new ValidarMetaMaestraCommand(actorId, "meta maestra");

        assertThatThrownBy(() -> service.validar(command)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("puerto APROBADA -> Veredicto.APROBADA con el feedback tal cual")
    void puertoAprobadaMapeaAVeredictoAprobada() {
        actorActivo();
        when(validacionPort.validar("meta completa")).thenReturn(
                new ResultadoValidacionMetaMaestra(ResultadoValidacionMetaMaestra.Estado.APROBADA, List.of(),
                        "Excelente, cubriste las 6 Ps."));

        var resultado = service.validar(new ValidarMetaMaestraCommand(actorId, "meta completa"));

        assertThat(resultado.veredicto()).isEqualTo(Veredicto.APROBADA);
        assertThat(resultado.feedback()).isEqualTo("Excelente, cubriste las 6 Ps.");
        assertThat(resultado.pesFaltantes()).isEmpty();
    }

    @Test
    @DisplayName("puerto RECHAZADA -> Veredicto.RECHAZADA con las Ps faltantes y el feedback")
    void puertoRechazadaMapeaAVeredictoRechazada() {
        actorActivo();
        when(validacionPort.validar("meta vaga")).thenReturn(
                new ResultadoValidacionMetaMaestra(ResultadoValidacionMetaMaestra.Estado.RECHAZADA,
                        List.of("CUANDO", "QUE_GANO"), "Te falta profundidad."));

        var resultado = service.validar(new ValidarMetaMaestraCommand(actorId, "meta vaga"));

        assertThat(resultado.veredicto()).isEqualTo(Veredicto.RECHAZADA);
        assertThat(resultado.pesFaltantes()).containsExactly("CUANDO", "QUE_GANO");
        assertThat(resultado.feedback()).isEqualTo("Te falta profundidad.");
    }

    @Test
    @DisplayName("puerto NO_DISPONIBLE (fallo tecnico) -> PENDIENTE_DE_REVISION, fail-open, nunca bloquea al aprendiz")
    void puertoNoDisponibleCaeAPendienteDeRevisionFailOpen() {
        actorActivo();
        when(validacionPort.validar("meta maestra")).thenReturn(ResultadoValidacionMetaMaestra.noDisponible());

        var resultado = service.validar(new ValidarMetaMaestraCommand(actorId, "meta maestra"));

        assertThat(resultado.veredicto()).isEqualTo(Veredicto.PENDIENTE_DE_REVISION);
        assertThat(resultado.pesFaltantes()).isEmpty();
    }
}
