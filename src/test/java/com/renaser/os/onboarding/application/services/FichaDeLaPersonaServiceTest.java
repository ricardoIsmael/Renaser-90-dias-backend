package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.api.FichaDeLaPersonaFinder.TipoDeFicha;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.diagnostico.VersionesDeFichaPort;
import com.renaser.os.onboarding.application.ports.out.ficha.RespuestasDelDiagnosticoPort;
import com.renaser.os.onboarding.application.ports.out.mapa.EtapaOnboardingPort;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FichaDeLaPersonaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-31T03:00:00Z"));

    @Mock
    private LoadEstadoOnboardingPort loadEstadoPort;
    @Mock
    private SaveEstadoOnboardingPort saveEstadoPort;
    @Mock
    private ConsultarActorPort actorPort;
    @Mock
    private RespuestasDelDiagnosticoPort respuestasPort;
    @Mock
    private EtapaOnboardingPort etapaPort;
    @Mock
    private VersionesDeFichaPort versiones;

    private FichaDeLaPersonaService service;
    private UserId persona;
    private UserId admin;

    @BeforeEach
    void setUp() {
        service = new FichaDeLaPersonaService(loadEstadoPort, saveEstadoPort, actorPort, respuestasPort, etapaPort,
                versiones, CLOCK);
        persona = UserId.of(UUID.randomUUID());
        admin = UserId.of(UUID.randomUUID());
    }

    private void personaActiva() {
        when(actorPort.deActor(persona)).thenReturn(Optional.of(new ActorOnboarding(persona, false)));
    }

    @Test
    @DisplayName("tipoDe(): sin fila es la ficha estándar, y no falla por eso")
    void sinFilaEsEstandar() {
        when(loadEstadoPort.deUsuario(persona)).thenReturn(Optional.empty());

        assertThat(service.tipoDe(persona)).isEqualTo(TipoDeFicha.ESTANDAR);
    }

    @Test
    @DisplayName("hayDiagnosticoPublicado(): solo si existe una versión publicada")
    void hayDiagnosticoPublicadoMiraLasVersiones() {
        when(versiones.ultima()).thenReturn(Optional.empty());
        assertThat(service.hayDiagnosticoPublicado()).isFalse();

        when(versiones.ultima()).thenReturn(Optional.of(new com.renaser.os.onboarding.domain.model.diagnostico.VersionPublicada(1,
                new com.renaser.os.onboarding.domain.model.diagnostico.FichaDelDiagnostico(java.util.List.of()), admin, "Ana", CLOCK.now())));
        assertThat(service.hayDiagnosticoPublicado()).isTrue();
    }

    @Test
    @DisplayName("asignarDiagnostico(): alguien que no abrió su onboarding recibe una fila nueva, todavía sin iniciar")
    void asignaCreandoLaFila() {
        personaActiva();
        when(etapaPort.flujosCompletados(persona)).thenReturn(Set.of());
        when(loadEstadoPort.deUsuario(persona)).thenReturn(Optional.empty());

        service.asignarDiagnostico(persona, admin);

        ArgumentCaptor<EstadoOnboarding> guardado = ArgumentCaptor.forClass(EstadoOnboarding.class);
        verify(saveEstadoPort).guardar(guardado.capture());
        assertThat(guardado.getValue().conDiagnostico()).isTrue();
        assertThat(guardado.getValue().fichaAsignadaPor()).isEqualTo(admin);
        assertThat(guardado.getValue().fichaAsignadaEn()).isEqualTo(CLOCK.now());
        assertThat(guardado.getValue().iniciadoEn()).isNull();
    }

    @Test
    @DisplayName("asignarDiagnostico(): a quien ya está dentro le cambia la ficha sin tocar el resto de su estado")
    void asignaAQuienYaEstaDentro() {
        personaActiva();
        when(etapaPort.flujosCompletados(persona)).thenReturn(Set.of());
        EstadoOnboarding existente = EstadoOnboarding.iniciar(persona, CLOCK);
        existente.marcarCompletado(CLOCK);
        when(loadEstadoPort.deUsuario(persona)).thenReturn(Optional.of(existente));

        service.asignarDiagnostico(persona, admin);

        verify(saveEstadoPort).guardar(existente);
        assertThat(existente.conDiagnostico()).isTrue();
        assertThat(existente.completado()).isTrue();
    }

    @Test
    @DisplayName("asignarDiagnostico(): repetirlo no cambia quién ni cuándo se lo asignó")
    void asignarEsIdempotente() {
        personaActiva();
        when(etapaPort.flujosCompletados(persona)).thenReturn(Set.of());
        EstadoOnboarding existente = EstadoOnboarding.conDiagnostico(persona, admin, CLOCK);
        when(loadEstadoPort.deUsuario(persona)).thenReturn(Optional.of(existente));

        service.asignarDiagnostico(persona, UserId.of(UUID.randomUUID()));

        assertThat(existente.fichaAsignadaPor()).isEqualTo(admin);
    }

    @Test
    @DisplayName("asignarDiagnostico(): quien ya terminó su diagnóstico -> 409, no se vuelve a asignar")
    void yaTerminoSuDiagnostico() {
        personaActiva();
        when(etapaPort.flujosCompletados(persona)).thenReturn(Set.of(Diagnostico.FLUJO));

        assertThatThrownBy(() -> service.asignarDiagnostico(persona, admin))
                .isInstanceOf(IllegalStateException.class);
        verify(saveEstadoPort, never()).guardar(any());
    }

    @Test
    @DisplayName("asignarDiagnostico(): cuenta suspendida -> 403 y cuenta inexistente -> 404")
    void suspendidaEInexistente() {
        when(actorPort.deActor(persona)).thenReturn(Optional.of(new ActorOnboarding(persona, true)));
        assertThatThrownBy(() -> service.asignarDiagnostico(persona, admin))
                .isInstanceOf(NotAuthorizedException.class);

        when(actorPort.deActor(persona)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.asignarDiagnostico(persona, admin))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("quitarDiagnostico(): sin respuestas vuelve a la ficha estándar")
    void quitaSinRespuestas() {
        personaActiva();
        EstadoOnboarding existente = EstadoOnboarding.conDiagnostico(persona, admin, CLOCK);
        when(loadEstadoPort.deUsuario(persona)).thenReturn(Optional.of(existente));
        when(respuestasPort.hayRespuestas(persona)).thenReturn(false);

        service.quitarDiagnostico(persona, admin);

        assertThat(existente.conDiagnostico()).isFalse();
        assertThat(existente.fichaAsignadaPor()).isNull();
        verify(saveEstadoPort).guardar(existente);
    }

    @Test
    @DisplayName("quitarDiagnostico(): con respuestas -> 409 y la ficha no cambia")
    void noQuitaConRespuestas() {
        personaActiva();
        EstadoOnboarding existente = EstadoOnboarding.conDiagnostico(persona, admin, CLOCK);
        when(loadEstadoPort.deUsuario(persona)).thenReturn(Optional.of(existente));
        when(respuestasPort.hayRespuestas(persona)).thenReturn(true);

        assertThatThrownBy(() -> service.quitarDiagnostico(persona, admin))
                .isInstanceOf(IllegalStateException.class);
        assertThat(existente.conDiagnostico()).isTrue();
        verify(saveEstadoPort, never()).guardar(any());
    }

    @Test
    @DisplayName("quitarDiagnostico(): a quien tiene la ficha estándar no le hace nada")
    void quitarEnEstandarNoHaceNada() {
        personaActiva();
        when(loadEstadoPort.deUsuario(persona)).thenReturn(Optional.of(EstadoOnboarding.iniciar(persona, CLOCK)));

        service.quitarDiagnostico(persona, admin);

        verify(saveEstadoPort, never()).guardar(any());
    }
}
