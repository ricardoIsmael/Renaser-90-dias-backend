package com.renaser.os.community.infrastructure.adapter.out.persistence.celula;

import com.renaser.os.community.application.ports.out.celula.ExistePerfilMentorPort;
import com.renaser.os.shared.domain.UserId;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Component;

@Component
class ExistePerfilMentorPersistenceAdapter implements ExistePerfilMentorPort {

    private static final String QUERY = "SELECT 1 FROM renaser.perfiles_mentor WHERE usuario_id = ?1";

    private final EntityManager entityManager;

    ExistePerfilMentorPersistenceAdapter(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean existe(UserId usuarioId) {
        return !entityManager.createNativeQuery(QUERY).setParameter(1, usuarioId.value()).getResultList().isEmpty();
    }
}
