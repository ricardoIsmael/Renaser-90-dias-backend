package com.renaser.os.community.infrastructure.adapter.out.persistence.categoria;

import com.renaser.os.community.domain.model.categoria.CategoriaMuro;
import org.springframework.stereotype.Component;

@Component
class CategoriaMuroPersistenceMapper {

    CategoriaMuro toDomain(CategoriaMuroJpaEntity e) {
        return CategoriaMuro.rehydrate(e.getClave(), e.getEtiqueta(), e.getEmoji(), e.getOrden(), e.isActiva(),
                e.isEsSistema(), e.getCreadoEn(), e.getActualizadoEn());
    }

    CategoriaMuroJpaEntity toEntity(CategoriaMuro c) {
        return new CategoriaMuroJpaEntity(c.clave(), c.etiqueta(), c.emoji(), c.orden(), c.activa(), c.esSistema(),
                c.creadoEn(), c.actualizadoEn());
    }
}
