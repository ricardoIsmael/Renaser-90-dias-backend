package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.media;

import com.renaser.os.onboarding.domain.model.media.ClaseMedia;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class MediaPersistenceMapper {

    MediaOnboarding toDomain(MediaOnboardingJpaEntity e) {
        return MediaOnboarding.rehydrate(e.getId(), UserId.of(e.getUsuarioId()), e.getFlujo(), e.getClavePregunta(),
                toDomainClase(e.getClase()), e.getBucket(), e.getRutaStorage(), e.getMime(), e.getTamanoBytes(),
                e.getDuracionSegundos(), e.getMetadatos(), e.getCreadoEn(), e.getActualizadoEn());
    }

    MediaOnboardingJpaEntity toEntity(MediaOnboarding m) {
        return new MediaOnboardingJpaEntity(m.id(), m.usuarioId().value(), m.flujo(), m.clavePregunta(),
                toJpaClase(m.clase()), m.bucket(), m.rutaStorage(), m.mime(), m.tamanoBytes(), m.duracionSegundos(),
                m.metadatos(), m.creadoEn(), m.actualizadoEn());
    }

    private String toJpaClase(ClaseMedia clase) {
        return switch (clase) {
            case AUDIO -> "audio";
            case FIRMA -> "firma";
            case DOCUMENTO -> "documento";
        };
    }

    private ClaseMedia toDomainClase(String jpa) {
        return switch (jpa) {
            case "audio" -> ClaseMedia.AUDIO;
            case "firma" -> ClaseMedia.FIRMA;
            case "documento" -> ClaseMedia.DOCUMENTO;
            default -> throw new IllegalStateException("clase de media desconocida en la base: " + jpa);
        };
    }
}
