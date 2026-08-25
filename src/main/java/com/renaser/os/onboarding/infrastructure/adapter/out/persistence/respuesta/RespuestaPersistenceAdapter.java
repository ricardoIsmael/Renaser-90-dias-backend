package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.respuesta;

import com.renaser.os.onboarding.application.ports.out.respuesta.LoadRespuestaPort;
import com.renaser.os.onboarding.application.ports.out.respuesta.SaveRespuestaPort;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
class RespuestaPersistenceAdapter implements LoadRespuestaPort, SaveRespuestaPort {

    private final SpringDataRespuestaOnboardingRepository repository;
    private final RespuestaPersistenceMapper mapper;

    RespuestaPersistenceAdapter(SpringDataRespuestaOnboardingRepository repository,
                                 RespuestaPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public Optional<Respuesta> porUsuarioYPregunta(UserId usuarioId, int preguntaId) {
        return repository.findByUsuarioIdAndPreguntaId(usuarioId.value(), preguntaId).map(mapper::toDomain);
    }

    @Override
    public List<Respuesta> todasDeUsuario(UserId usuarioId) {
        return repository.findByUsuarioId(usuarioId.value()).stream().map(mapper::toDomain).toList();
    }

    /**
     * UPSERT por {@code (usuarioId, preguntaId)}: si ya existe una fila para esa clave, se
     * reutiliza su id (Hibernate hace UPDATE) — asi guardar dos veces sobre la misma
     * pregunta nunca duplica (ver javadoc de {@code SaveRespuestaPort}).
     */
    @Override
    public Respuesta guardar(Respuesta respuesta) {
        RespuestaOnboardingJpaEntity entity = mapper.toEntity(respuesta);
        if (entity.getId() == null) {
            repository.findByUsuarioIdAndPreguntaId(respuesta.usuarioId().value(), respuesta.preguntaId())
                    .ifPresent(existente -> entity.setId(existente.getId()));
        }
        var saved = repository.saveAndFlush(entity);
        return mapper.toDomain(saved);
    }
}
