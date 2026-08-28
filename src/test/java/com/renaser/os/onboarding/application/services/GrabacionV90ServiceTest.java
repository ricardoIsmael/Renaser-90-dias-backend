package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.grabacionv90.RegistrarGrabacionV90UseCase.RegistrarGrabacionV90Command;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase.ConsultarEstadoV90Query;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase.SolicitarValidacionV90Command;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.DespacharValidacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.LoadGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.grabacionv90.SaveGrabacionV90Port;
import com.renaser.os.onboarding.application.ports.out.media.LoadMediaPort;
import com.renaser.os.onboarding.domain.model.grabacionv90.EstadoIAv90;
import com.renaser.os.onboarding.domain.model.grabacionv90.GrabacionV90;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
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
class GrabacionV90ServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private LoadGrabacionV90Port loadGrabacionPort;
    @Mock
    private SaveGrabacionV90Port saveGrabacionPort;
    @Mock
    private LoadMediaPort loadMediaPort;
    @Mock
    private DespacharValidacionV90Port despacharPort;
    @Mock
    private ConsultarActorPort actorPort;

    private GrabacionV90Service service;
    private UserId usuarioId;

    @BeforeEach
    void setUp() {
        service = new GrabacionV90Service(loadGrabacionPort, saveGrabacionPort, loadMediaPort, despacharPort,
                actorPort, CLOCK);
        usuarioId = UserId.of(UUID.randomUUID());
    }

    private void actorActivo() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
    }

    private GrabacionV90 grabacionGrabadaDe(UserId propietario) {
        GrabacionV90 g = GrabacionV90.crearSlot(propietario, "FASE_1", "MENTE", (short) 0, "v90_mente_0", CLOCK);
        g.marcarGrabada(1L, null, "transcripcion", CLOCK);
        return g;
    }

    // ── seguridad: un usuario no puede tocar la grabacion de otro ──────────

    @Test
    @DisplayName("consultarEstado(): grabacion de OTRO usuario -> NotAuthorizedException (403), no filtra por 404")
    void consultarEstadoDeOtroUsuario() {
        actorActivo();
        UserId otro = UserId.of(UUID.randomUUID());
        GrabacionV90 deOtro = grabacionGrabadaDe(otro);
        when(loadGrabacionPort.porId(5L)).thenReturn(Optional.of(deOtro));

        assertThatThrownBy(() -> service.consultarEstado(new ConsultarEstadoV90Query(usuarioId, 5L)))
                .isInstanceOf(NotAuthorizedException.class);
    }

    @Test
    @DisplayName("solicitarValidacion(): grabacion de OTRO usuario -> NotAuthorizedException, nunca despacha")
    void solicitarValidacionDeOtroUsuario() {
        actorActivo();
        UserId otro = UserId.of(UUID.randomUUID());
        GrabacionV90 deOtro = grabacionGrabadaDe(otro);
        when(loadGrabacionPort.porId(5L)).thenReturn(Optional.of(deOtro));

        assertThatThrownBy(() -> service.solicitarValidacion(new SolicitarValidacionV90Command(usuarioId, 5L)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(despacharPort, never()).despachar(any(), org.mockito.ArgumentMatchers.anyLong());
        verify(saveGrabacionPort, never()).guardar(any());
    }

    @Test
    @DisplayName("consultarEstado(): grabacion inexistente -> NoSuchElementException (404)")
    void consultarEstadoInexistente() {
        actorActivo();
        when(loadGrabacionPort.porId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultarEstado(new ConsultarEstadoV90Query(usuarioId, 5L)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("actor suspendido -> NotAuthorizedException antes de tocar la grabacion")
    void actorSuspendidoNoLlegaALaGrabacion() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, true)));

        assertThatThrownBy(() -> service.consultarEstado(new ConsultarEstadoV90Query(usuarioId, 5L)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(loadGrabacionPort, never()).porId(org.mockito.ArgumentMatchers.anyLong());
    }

    // ── registrar ────────────────────────────────────────────────────────

    @Test
    @DisplayName("registrar(): media de otro usuario -> NoSuchElementException, nunca guarda")
    void registrarConMediaDeOtroUsuario() {
        actorActivo();
        when(loadMediaPort.porIdYUsuario(1L, usuarioId)).thenReturn(Optional.empty());

        var comando = new RegistrarGrabacionV90Command(usuarioId, "FASE_1", "MENTE", (short) 0, "v90_mente_0", 1L,
                null, "texto");

        assertThatThrownBy(() -> service.registrar(comando)).isInstanceOf(NoSuchElementException.class);
        verify(saveGrabacionPort, never()).guardar(any());
    }

    @Test
    @DisplayName("registrar(): slot nuevo -> crea grabada=true")
    void registrarSlotNuevo() {
        actorActivo();
        MediaOnboarding media = MediaOnboarding.registrar(usuarioId, "v90", "v90_mente_0",
                com.renaser.os.onboarding.domain.model.media.ClaseMedia.AUDIO, MediaOnboarding.BUCKET_DEFAULT,
                "onboarding/" + usuarioId + "/audio/ruta", null, null, null, null, CLOCK);
        when(loadMediaPort.porIdYUsuario(1L, usuarioId)).thenReturn(Optional.of(media));
        when(loadGrabacionPort.porSlot(usuarioId, "FASE_1", "MENTE", (short) 0)).thenReturn(Optional.empty());
        when(saveGrabacionPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        var comando = new RegistrarGrabacionV90Command(usuarioId, "FASE_1", "MENTE", (short) 0, "v90_mente_0", 1L,
                null, "texto");

        GrabacionV90 resultado = service.registrar(comando);

        assertThat(resultado.grabada()).isTrue();
        assertThat(resultado.estadoIa()).isEqualTo(EstadoIAv90.PENDIENTE);
    }
}
