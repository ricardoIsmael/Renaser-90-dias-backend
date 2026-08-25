package com.renaser.os.chat.infrastructure.adapter.out.persistence.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class MensajePersistenceMapper {

    Mensaje toDomain(MensajeJpaEntity e) {
        return Mensaje.rehydrate(MensajeId.of(e.getId()), ConversacionId.of(e.getConversacionId()),
                UserId.of(e.getEmisorId()), TipoMensaje.valueOf(e.getTipo().name()), e.getTexto(),
                e.getMediaBucket(), e.getMediaRuta(), e.getMediaMime(), e.getMediaBytes(), e.getMediaDuracionS(),
                e.isOculto(), e.getEliminadoEn(), e.getRespuestaAId() != null ? MensajeId.of(e.getRespuestaAId()) : null,
                e.getCreadoEn());
    }

    MensajeJpaEntity toEntity(Mensaje m) {
        return new MensajeJpaEntity(m.id().value(), m.conversacionId().value(), m.emisorId().value(),
                TipoMensajeJpa.valueOf(m.tipo().name()), m.texto(), m.mediaBucket(), m.mediaRuta(), m.mediaMime(),
                m.mediaBytes(), m.mediaDuracionS(), m.oculto(), m.eliminadoEn(),
                m.respuestaAId() != null ? m.respuestaAId().value() : null, m.creadoEn());
    }
}
