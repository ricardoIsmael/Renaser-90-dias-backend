package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.media.VerFirmaDelPactoUseCase.FirmaParaVer;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort.ActorOnboarding;
import com.renaser.os.onboarding.application.ports.out.media.LoadMediaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.LeerRespuestasPorClavePort;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** D-253: la URL de la firma del Pacto sale solo para su dueña, y solo si hay una firma de verdad. */
@ExtendWith(MockitoExtension.class)
class FirmaDelPactoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-05T03:30:00Z"));
    private static final long MEDIA_ID = 41L;

    @Mock
    private LeerRespuestasPorClavePort respuestasPort;
    @Mock
    private LoadMediaPort mediaPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private ConsultarActorPort actorPort;

    private FirmaDelPactoService service;
    private UserId ana;
    private UserId luis;

    @BeforeEach
    void setUp() {
        service = new FirmaDelPactoService(respuestasPort, mediaPort, almacenamientoPort, actorPort, CLOCK);
        ana = UserId.of(UUID.randomUUID());
        luis = UserId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("la dueña recibe la URL firmada de SU firma, por 15 minutos, con el vencimiento")
    void laDuenaRecibeSuFirma() {
        activo(ana);
        MediaOnboarding firma = media(ana, ClaseMedia.FIRMA);
        when(respuestasPort.mediaDe(ana, "signature")).thenReturn(Optional.of(MEDIA_ID));
        when(mediaPort.porId(MEDIA_ID)).thenReturn(Optional.of(firma));
        URI url = URI.create("https://s3.example/" + firma.rutaStorage() + "?X-Amz-Signature=abc");
        when(almacenamientoPort.firmarLectura(firma.rutaStorage(), Duration.ofMinutes(15))).thenReturn(url);

        FirmaParaVer resultado = service.deActor(ana);

        assertThat(resultado.url()).isEqualTo(url);
        assertThat(resultado.venceEn()).isEqualTo(Instant.parse("2026-10-05T03:45:00Z"));
    }

    @Test
    @DisplayName("sin respuesta a la firma del Pacto: 404 claro y no se firma nada")
    void sinFirmaEs404() {
        activo(ana);
        when(respuestasPort.mediaDe(ana, "signature")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deActor(ana))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessage("Todavía no hay una firma del Pacto guardada");
        verifyNoInteractions(mediaPort, almacenamientoPort);
    }

    @Test
    @DisplayName("la respuesta apunta a un archivo que ya no existe: 404, no un 500")
    void archivoBorradoEs404() {
        activo(ana);
        when(respuestasPort.mediaDe(ana, "signature")).thenReturn(Optional.of(MEDIA_ID));
        when(mediaPort.porId(MEDIA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deActor(ana)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(almacenamientoPort);
    }

    @Test
    @DisplayName("E-528: la respuesta de Luis apunta al archivo de Ana → 403, y la firma de Ana no se firma")
    void respuestaQueApuntaAUnArchivoAjenoEs403() {
        activo(luis);
        when(respuestasPort.mediaDe(luis, "signature")).thenReturn(Optional.of(MEDIA_ID));
        when(mediaPort.porId(MEDIA_ID)).thenReturn(Optional.of(media(ana, ClaseMedia.FIRMA)));

        assertThatThrownBy(() -> service.deActor(luis))
                .isInstanceOf(NotAuthorizedException.class)
                .hasMessage("Esa firma no es tuya");
        verify(almacenamientoPort, never()).firmarLectura(anyString(), any(Duration.class));
    }

    @Test
    @DisplayName("la respuesta apunta a un audio propio, no a una firma: 404 y no se firma")
    void archivoQueNoEsFirmaEs404() {
        activo(ana);
        when(respuestasPort.mediaDe(ana, "signature")).thenReturn(Optional.of(MEDIA_ID));
        when(mediaPort.porId(MEDIA_ID)).thenReturn(Optional.of(media(ana, ClaseMedia.AUDIO)));

        assertThatThrownBy(() -> service.deActor(ana)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(almacenamientoPort);
    }

    @Test
    @DisplayName("cuenta suspendida: 403 antes de leer ninguna respuesta")
    void suspendidaEs403() {
        when(actorPort.deActor(ana)).thenReturn(Optional.of(new ActorOnboarding(ana, true)));

        assertThatThrownBy(() -> service.deActor(ana)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(respuestasPort, mediaPort, almacenamientoPort);
    }

    @Test
    @DisplayName("actor inexistente: 404")
    void actorInexistenteEs404() {
        when(actorPort.deActor(ana)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deActor(ana)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(respuestasPort, mediaPort, almacenamientoPort);
    }

    private void activo(UserId usuario) {
        when(actorPort.deActor(usuario)).thenReturn(Optional.of(new ActorOnboarding(usuario, false)));
    }

    private static MediaOnboarding media(UserId dueno, ClaseMedia clase) {
        return MediaOnboarding.rehydrate(MEDIA_ID, dueno, "pacto", "signature", clase, MediaOnboarding.BUCKET_DEFAULT,
                "onboarding/" + dueno + "/" + clase.name().toLowerCase() + "/" + UUID.randomUUID(), "image/png", null,
                null, null, CLOCK.now(), CLOCK.now());
    }
}
