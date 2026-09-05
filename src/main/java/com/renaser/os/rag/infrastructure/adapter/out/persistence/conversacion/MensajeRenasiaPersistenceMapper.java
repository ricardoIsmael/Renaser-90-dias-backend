package com.renaser.os.rag.infrastructure.adapter.out.persistence.conversacion;

import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.FuenteMensaje;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class MensajeRenasiaPersistenceMapper {

    MensajeRenasia toDomain(MensajeRenasiaJpaEntity e, List<String> leccionIds) {
        List<FuenteMensaje> fuentes = leccionIds.stream().map(FuenteMensaje::of).toList();
        return MensajeRenasia.rehydrate(MensajeRenasiaId.of(e.getId()), UserId.of(e.getUsuarioId()),
                AgenteConversacional.valueOf(e.getAgente()), RolMensaje.valueOf(e.getRol().name()), e.getContenido(),
                fuentes, e.getCreadoEn());
    }

    /** D-49: {@code marcadoPorUsuario}/{@code notaMarca}/{@code anuladoPorAdmin} siempre con
     * su valor por defecto — el dominio no los conoce, no hay caso de uso que los mute. */
    MensajeRenasiaJpaEntity toEntity(MensajeRenasia m) {
        return new MensajeRenasiaJpaEntity(m.id().value(), m.usuarioId().value(),
                RolMensajeRenasiaJpa.valueOf(m.rol().name()), m.agente().name(), m.contenido(), false, null, false,
                m.creadoEn());
    }

    List<FuenteMensajeRenasiaJpaEntity> toFuenteEntities(MensajeRenasia m) {
        return m.fuentes().stream()
                .map(fuente -> new FuenteMensajeRenasiaJpaEntity(m.id().value(), fuente.leccionId()))
                .toList();
    }
}
