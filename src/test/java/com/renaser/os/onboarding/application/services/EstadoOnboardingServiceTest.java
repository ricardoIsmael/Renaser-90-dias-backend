package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.estado.AceptarHitoOnboardingUseCase.AceptarHitoCommand;
import com.renaser.os.onboarding.application.ports.in.estado.AvanzarEstadoUseCase.AvanzarEstadoCommand;
import com.renaser.os.onboarding.application.ports.in.estado.CompletarOnboardingUseCase.CompletarOnboardingCommand;
import com.renaser.os.onboarding.application.ports.in.terminos.RegistrarAceptacionDeTerminosUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.estado.LoadEstadoOnboardingPort;
import com.renaser.os.onboarding.application.ports.out.estado.SaveEstadoOnboardingPort;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
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
class EstadoOnboardingServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadEstadoOnboardingPort loadEstadoPort;
    @Mock
    private SaveEstadoOnboardingPort saveEstadoPort;
    @Mock
    private ConsultarActorPort actorPort;
    @Mock
    private com.renaser.os.onboarding.application.ports.in.terminos.RegistrarAceptacionDeTerminosUseCase aceptacionDeTerminos;

    private EstadoOnboardingService service;
    private UserId usuarioId;

    @BeforeEach
    void setUp() {
        service = new EstadoOnboardingService(loadEstadoPort, saveEstadoPort, actorPort, aceptacionDeTerminos, CLOCK);
        usuarioId = UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("obtener(): primera vez -> inicializa y guarda una fila nueva")
    void obtenerPrimeraVezInicializa() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
        when(loadEstadoPort.deUsuario(usuarioId)).thenReturn(Optional.empty());
        when(saveEstadoPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        EstadoOnboarding resultado = service.obtener(usuarioId);

        assertThat(resultado.usuarioId()).isEqualTo(usuarioId);
        assertThat(resultado.completado()).isFalse();
        verify(saveEstadoPort).guardar(any());
    }

    @Test
    @DisplayName("obtener(): ya existe -> no la vuelve a crear")
    void obtenerYaExisteNoRecrea() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
        EstadoOnboarding existente = EstadoOnboarding.iniciar(usuarioId, CLOCK);
        when(loadEstadoPort.deUsuario(usuarioId)).thenReturn(Optional.of(existente));

        EstadoOnboarding resultado = service.obtener(usuarioId);

        assertThat(resultado).isSameAs(existente);
        verify(saveEstadoPort, never()).guardar(any());
    }

    @Test
    @DisplayName("actor suspendido -> NotAuthorizedException (403) en cualquier operacion")
    void actorSuspendidoRechazaTodo() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, true)));

        assertThatThrownBy(() -> service.obtener(usuarioId)).isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("actor inexistente -> NoSuchElementException (404)")
    void actorInexistente() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtener(usuarioId)).isInstanceOf(NoSuchElementException.class);
    }

    // decisión 2026-11-02 pagos fuera de términos
    @Test
    @DisplayName("aceptar(): delega en EstadoOnboarding.aceptarHito y guarda")
    void aceptarDelegaEnElDominio() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
        EstadoOnboarding existente = EstadoOnboarding.iniciar(usuarioId, CLOCK);
        when(loadEstadoPort.deUsuario(usuarioId)).thenReturn(Optional.of(existente));
        when(saveEstadoPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        EstadoOnboarding resultado = service.aceptar(new AceptarHitoCommand(usuarioId, HitoOnboarding.TERMINOS, null, null));

        assertThat(resultado.terminosAceptadosEn()).isEqualTo(CLOCK.now());
        verify(aceptacionDeTerminos).registrar(usuarioId, new RegistrarAceptacionDeTerminosUseCase.PedidoDeAceptacion(null, null));
    }

    // spec 039 FR-007; decisión 2026-11-02 pagos fuera de términos
    @Test
    @DisplayName("aceptar() de Términos con un cronograma ya anotado: la constancia lleva la versión y el inicio que vio, y NO confirma ni toca el cronograma (se confirma en «Mi ingreso»)")
    void aceptarTerminosNoToqueElCronograma() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
        EstadoOnboarding existente = EstadoOnboarding.iniciar(usuarioId, CLOCK);
        existente.anotarConCronograma(Moneda.PEN, CLOCK);
        when(loadEstadoPort.deUsuario(usuarioId)).thenReturn(Optional.of(existente));
        when(saveEstadoPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));
        UUID version = UUID.randomUUID();

        EstadoOnboarding resultado = service.aceptar(new AceptarHitoCommand(usuarioId, HitoOnboarding.TERMINOS, version, LocalDate.parse("2026-08-25")));

        verify(aceptacionDeTerminos).registrar(usuarioId, new RegistrarAceptacionDeTerminosUseCase.PedidoDeAceptacion(version, LocalDate.parse("2026-08-25")));
        assertThat(resultado.terminosAceptadosEn()).isEqualTo(CLOCK.now());
        assertThat(resultado.pago().esConCronograma()).isTrue();
        assertThat(resultado.pago().confirmadoEn()).as("firmar los términos no confirma el cronograma").isNull();
        assertThat(resultado.pago().confirmadoNombre()).isNull();
        assertThat(resultado.pago().confirmadoHuella()).isNull();
    }

    @Test
    @DisplayName("aceptar() de otro hito no toca la aceptación de términos")
    void otroHitoNoRegistraTerminos() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
        when(loadEstadoPort.deUsuario(usuarioId)).thenReturn(Optional.of(EstadoOnboarding.iniciar(usuarioId, CLOCK)));
        when(saveEstadoPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        service.aceptar(new AceptarHitoCommand(usuarioId, HitoOnboarding.PACTO, null, null));

        verify(aceptacionDeTerminos, never()).registrar(any(), any());
    }

    @Test
    @DisplayName("avanzar(): mueve flujo/seccion/paso")
    void avanzarMueveElCursor() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
        EstadoOnboarding existente = EstadoOnboarding.iniciar(usuarioId, CLOCK);
        when(loadEstadoPort.deUsuario(usuarioId)).thenReturn(Optional.of(existente));
        when(saveEstadoPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        EstadoOnboarding resultado = service.avanzar(
                new AvanzarEstadoCommand(usuarioId, "v90", "seccion-1", 1, null));

        assertThat(resultado.flujoActual()).isEqualTo("v90");
        assertThat(resultado.seccionActual()).isEqualTo("seccion-1");
    }

    @Test
    @DisplayName("completar(): marca completado")
    void completarMarcaCompletado() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
        EstadoOnboarding existente = EstadoOnboarding.iniciar(usuarioId, CLOCK);
        when(loadEstadoPort.deUsuario(usuarioId)).thenReturn(Optional.of(existente));
        when(saveEstadoPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        EstadoOnboarding resultado = service.completar(new CompletarOnboardingCommand(usuarioId));

        assertThat(resultado.completado()).isTrue();
    }

    @Test
    @DisplayName("completado() (api): false si nunca abrio el onboarding")
    void completadoApiFalseSiNuncaAbrio() {
        when(loadEstadoPort.deUsuario(usuarioId)).thenReturn(Optional.empty());

        assertThat(service.completado(usuarioId)).isFalse();
    }

    @Test
    @DisplayName("pactoFase1Firmado() (api): true solo si pactoFirmadoEn no es null")
    void pactoFase1FirmadoApi() {
        EstadoOnboarding estado = EstadoOnboarding.iniciar(usuarioId, CLOCK);
        when(loadEstadoPort.deUsuario(usuarioId)).thenReturn(Optional.of(estado));
        assertThat(service.pactoFase1Firmado(usuarioId)).isFalse();

        estado.aceptarHito(HitoOnboarding.PACTO_FIRMADO, CLOCK);
        assertThat(service.pactoFase1Firmado(usuarioId)).isTrue();
    }
}
