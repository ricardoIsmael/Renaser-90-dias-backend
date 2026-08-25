package com.renaser.os.onboarding.application.services;

import com.renaser.os.onboarding.application.ports.in.media.ObtenerUrlSubidaMediaUseCase;
import com.renaser.os.onboarding.application.ports.in.media.RegistrarMediaUseCase;
import com.renaser.os.onboarding.application.ports.out.actor.ConsultarActorPort;
import com.renaser.os.onboarding.application.ports.out.media.SaveMediaPort;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.NoSuchElementException;

@Service
public class MediaService implements ObtenerUrlSubidaMediaUseCase, RegistrarMediaUseCase {

    private static final Duration VALIDEZ_URL_SUBIDA = Duration.ofMinutes(15);

    private final SaveMediaPort saveMediaPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final ConsultarActorPort actorPort;
    private final Clock clock;

    public MediaService(SaveMediaPort saveMediaPort, AlmacenamientoPort almacenamientoPort,
                         ConsultarActorPort actorPort, Clock clock) {
        this.saveMediaPort = saveMediaPort;
        this.almacenamientoPort = almacenamientoPort;
        this.actorPort = actorPort;
        this.clock = clock;
    }

    @Override
    public UrlSubidaMedia obtener(ObtenerUrlSubidaMediaCommand command) {
        requireActorActivo(command.usuarioId());
        String ruta = MediaOnboarding.rutaNueva(command.usuarioId(), command.clase());
        URI url = almacenamientoPort.firmarSubida(ruta, command.tipoContenido(), VALIDEZ_URL_SUBIDA);
        return new UrlSubidaMedia(url, MediaOnboarding.BUCKET_DEFAULT, ruta);
    }

    @Override
    @Transactional
    public MediaOnboarding registrar(RegistrarMediaCommand command) {
        requireActorActivo(command.usuarioId());
        MediaOnboarding media = MediaOnboarding.registrar(command.usuarioId(), command.flujo(),
                command.clavePregunta(), command.clase(), command.bucket(), command.rutaStorage(), command.mime(),
                command.tamanoBytes(), command.duracionSegundos(), command.metadatosJson(), clock);
        return saveMediaPort.guardar(media);
    }

    private void requireActorActivo(UserId actorId) {
        ConsultarActorPort.ActorOnboarding actor = actorPort.deActor(actorId)
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + actorId));
        if (actor.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
    }
}
