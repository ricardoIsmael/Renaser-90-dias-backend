package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class ConversacionRenasiaPersistenceMapper {

    ConversacionRenasia toDomain(ConversacionRenasiaJpaEntity e) {
        return ConversacionRenasia.rehydrate(UserId.of(e.getUsuarioId()), e.getCreadoEn(), e.getActualizadoEn());
    }

    ConversacionRenasiaJpaEntity toEntity(ConversacionRenasia c) {
        return new ConversacionRenasiaJpaEntity(c.usuarioId().value(), c.creadoEn(), c.actualizadoEn());
    }
}
