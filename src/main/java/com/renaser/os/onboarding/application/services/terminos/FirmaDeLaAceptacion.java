package com.renaser.os.onboarding.application.services.terminos;

import com.renaser.os.onboarding.application.ports.out.media.LoadMediaPort;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * El enlace de lectura de la firma dibujada al aceptar unos términos (spec 027): lo usan la constancia que ve Administración y la relectura de la propia
 * persona (FR-023). La firma es un archivo privado; el enlace vence a los 15 minutos, como el de la firma del Pacto y las fotos de evidencia.
 */
@Component
class FirmaDeLaAceptacion {

    private static final Duration VALIDEZ = Duration.ofMinutes(15);

    private final LoadMediaPort medias;
    private final AlmacenamientoPort almacenamiento;

    FirmaDeLaAceptacion(LoadMediaPort medias, AlmacenamientoPort almacenamiento) {
        this.medias = medias;
        this.almacenamiento = almacenamiento;
    }

    /** Vacío si la aceptación no guardó firma o el archivo ya no está (o no es una firma). */
    Optional<String> urlDe(Long mediaId) {
        if (mediaId == null) {
            return Optional.empty();
        }
        return medias.porId(mediaId).filter(MediaOnboarding::esFirma).map(m -> almacenamiento.firmarLectura(m.rutaStorage(), VALIDEZ).toString());
    }
}
