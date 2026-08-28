package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.media.ObtenerUrlSubidaMediaUseCase.ObtenerUrlSubidaMediaCommand;
import com.renaser.os.onboarding.application.ports.in.media.RegistrarMediaUseCase.RegistrarMediaCommand;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.media.SaveMediaPort;
import com.renaser.os.onboarding.domain.model.media.ClaseMedia;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Mock
    private SaveMediaPort saveMediaPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private ConsultarActorPort actorPort;

    private MediaService service;
    private UserId usuarioId;

    @BeforeEach
    void setUp() {
        service = new MediaService(saveMediaPort, almacenamientoPort, actorPort, CLOCK);
        usuarioId = UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("obtener(): delega en AlmacenamientoPort.firmarSubida con la ruta nueva generada")
    void obtenerDelegaEnAlmacenamientoPort() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
        URI url = URI.create("https://s3.example/onboarding/x/audio/uuid?sig=abc");
        when(almacenamientoPort.firmarSubida(anyString(), anyString(), any(Duration.class))).thenReturn(url);

        var comando = new ObtenerUrlSubidaMediaCommand(usuarioId, "v90", "clave-1", ClaseMedia.AUDIO, "audio/mpeg");
        var resultado = service.obtener(comando);

        assertThat(resultado.urlSubida()).isEqualTo(url);
        assertThat(resultado.bucket()).isEqualTo(MediaOnboarding.BUCKET_DEFAULT);

        ArgumentCaptor<String> rutaCaptor = ArgumentCaptor.forClass(String.class);
        verify(almacenamientoPort).firmarSubida(rutaCaptor.capture(), org.mockito.ArgumentMatchers.eq("audio/mpeg"),
                any(Duration.class));
        assertThat(rutaCaptor.getValue()).startsWith("onboarding/" + usuarioId + "/audio/");
        assertThat(resultado.ruta()).isEqualTo(rutaCaptor.getValue());
    }

    @Test
    @DisplayName("obtener(): actor suspendido -> NotAuthorizedException, nunca pide URL")
    void obtenerConActorSuspendido() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, true)));

        var comando = new ObtenerUrlSubidaMediaCommand(usuarioId, "v90", "clave-1", ClaseMedia.AUDIO, "audio/mpeg");

        assertThatThrownBy(() -> service.obtener(comando)).isInstanceOf(NotAuthorizedException.class);
        verify(almacenamientoPort, never()).firmarSubida(anyString(), anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("registrar(): confirma la subida creando la fila via SaveMediaPort")
    void registrarConfirmaLaSubida() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
        when(saveMediaPort.guardar(any())).thenAnswer(inv -> inv.getArgument(0));

        String ruta = "onboarding/" + usuarioId + "/audio/uuid";
        var comando = new RegistrarMediaCommand(usuarioId, "v90", "clave-1", ClaseMedia.AUDIO,
                MediaOnboarding.BUCKET_DEFAULT, ruta, "audio/mpeg", 1024L, null, null);

        MediaOnboarding resultado = service.registrar(comando);

        assertThat(resultado.usuarioId()).isEqualTo(usuarioId);
        assertThat(resultado.rutaStorage()).isEqualTo(ruta);
        verify(saveMediaPort).guardar(any());
    }

    @Test
    @DisplayName("registrar(): rechaza una ruta que no cae bajo el prefijo del propio usuario")
    void registrarRechazaRutaDeOtroUsuario() {
        when(actorPort.deActor(usuarioId)).thenReturn(Optional.of(new ActorOnboarding(usuarioId, false)));
        UserId otroUsuarioId = UserId.of(UUID.randomUUID());

        var comando = new RegistrarMediaCommand(usuarioId, "v90", "clave-1", ClaseMedia.AUDIO,
                MediaOnboarding.BUCKET_DEFAULT, "onboarding/" + otroUsuarioId + "/audio/uuid", "audio/mpeg", 1024L,
                null, null);

        assertThatThrownBy(() -> service.registrar(comando)).isInstanceOf(IllegalArgumentException.class);
        verify(saveMediaPort, never()).guardar(any());
    }
}
