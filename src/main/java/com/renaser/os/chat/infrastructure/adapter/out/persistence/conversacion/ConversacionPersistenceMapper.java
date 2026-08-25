package com.renaser.os.chat.infrastructure.adapter.out.persistence.conversacion;

import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import org.springframework.stereotype.Component;

@Component
class ConversacionPersistenceMapper {

    Conversacion toDomain(ConversacionJpaEntity e) {
        return Conversacion.rehydrate(ConversacionId.of(e.getId()), TipoConversacion.valueOf(e.getTipo().name()),
                e.getCelulaId(), e.getClaveDirecta(), e.getNombre(), e.getCreadoEn());
    }

    ConversacionJpaEntity toEntity(Conversacion c) {
        return new ConversacionJpaEntity(c.id().value(), TipoConversacionJpa.valueOf(c.tipo().name()), c.celulaId(),
                c.claveDirecta(), c.nombre(), c.creadoEn());
    }
}
