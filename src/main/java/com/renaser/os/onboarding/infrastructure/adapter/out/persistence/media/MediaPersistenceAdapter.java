package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.media;

import com.renaser.os.onboarding.application.ports.out.media.LoadMediaPort;
import com.renaser.os.onboarding.application.ports.out.media.SaveMediaPort;
import com.renaser.os.onboarding.domain.model.media.MediaOnboarding;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
class MediaPersistenceAdapter implements SaveMediaPort, LoadMediaPort {

    private final SpringDataMediaOnboardingRepository repository;
    private final MediaPersistenceMapper mapper;

    MediaPersistenceAdapter(SpringDataMediaOnboardingRepository repository, MediaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public MediaOnboarding guardar(MediaOnboarding media) {
        var saved = repository.saveAndFlush(mapper.toEntity(media));
        return mapper.toDomain(saved);
    }

    @Override
    public Optional<MediaOnboarding> porId(long mediaId) {
        return repository.findById(mediaId).map(mapper::toDomain);
    }

    @Override
    public Optional<MediaOnboarding> porIdYUsuario(long mediaId, UserId usuarioId) {
        return repository.findByIdAndUsuarioId(mediaId, usuarioId.value()).map(mapper::toDomain);
    }
}
