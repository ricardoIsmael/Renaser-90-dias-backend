package com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion;

import com.renaser.os.notifications.domain.model.notificacion.Notificacion;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class NotificacionPersistenceMapper {

    Notificacion toDomain(NotificacionJpaEntity e) {
        return Notificacion.rehydrate(e.getId(), UserId.of(e.getUsuarioId()), toDomainTipo(e.getTipo()),
                e.getTitulo(), e.getCuerpo(), e.getRutaApp(), e.getLeidaEn(), e.getCreadoEn(),
                e.getOrigenEventoId());
    }

    NotificacionJpaEntity toEntity(Notificacion n) {
        return new NotificacionJpaEntity(n.id(), n.usuarioId().value(), toJpaTipo(n.tipo()), n.titulo(), n.cuerpo(),
                n.rutaApp(), n.leidaEn(), n.creadoEn(), n.origenEventoId());
    }

    private TipoNotificacionJpa toJpaTipo(TipoNotificacion tipo) {
        return switch (tipo) {
            case RECORDATORIO_HABITO -> TipoNotificacionJpa.RECORDATORIO_HABITO;
            case RECORDATORIO_ROCA -> TipoNotificacionJpa.RECORDATORIO_ROCA;
            case RECORDATORIO_RADAR -> TipoNotificacionJpa.RECORDATORIO_RADAR;
            case MENSAJE_MENTOR -> TipoNotificacionJpa.MENSAJE_MENTOR;
            case ANUNCIO_SISTEMA -> TipoNotificacionJpa.ANUNCIO_SISTEMA;
            case RESUMEN_SEMANAL -> TipoNotificacionJpa.RESUMEN_SEMANAL;
            case LOGRO_DESBLOQUEADO -> TipoNotificacionJpa.LOGRO_DESBLOQUEADO;
            case HITO_PROGRAMA -> TipoNotificacionJpa.HITO_PROGRAMA;
            case MENSAJE_CHAT -> TipoNotificacionJpa.MENSAJE_CHAT;
            case TICKET_RESPONDIDO -> TipoNotificacionJpa.TICKET_RESPONDIDO;
            case TICKET_ABIERTO -> TipoNotificacionJpa.TICKET_ABIERTO;
            case SANTUARIO_ROTO -> TipoNotificacionJpa.SANTUARIO_ROTO;
            case HABITO_PERSONAL_MODIFICADO -> TipoNotificacionJpa.HABITO_PERSONAL_MODIFICADO;
        };
    }

    private TipoNotificacion toDomainTipo(TipoNotificacionJpa jpa) {
        return switch (jpa) {
            case RECORDATORIO_HABITO -> TipoNotificacion.RECORDATORIO_HABITO;
            case RECORDATORIO_ROCA -> TipoNotificacion.RECORDATORIO_ROCA;
            case RECORDATORIO_RADAR -> TipoNotificacion.RECORDATORIO_RADAR;
            case MENSAJE_MENTOR -> TipoNotificacion.MENSAJE_MENTOR;
            case ANUNCIO_SISTEMA -> TipoNotificacion.ANUNCIO_SISTEMA;
            case RESUMEN_SEMANAL -> TipoNotificacion.RESUMEN_SEMANAL;
            case LOGRO_DESBLOQUEADO -> TipoNotificacion.LOGRO_DESBLOQUEADO;
            case HITO_PROGRAMA -> TipoNotificacion.HITO_PROGRAMA;
            case MENSAJE_CHAT -> TipoNotificacion.MENSAJE_CHAT;
            case TICKET_RESPONDIDO -> TipoNotificacion.TICKET_RESPONDIDO;
            case TICKET_ABIERTO -> TipoNotificacion.TICKET_ABIERTO;
            case SANTUARIO_ROTO -> TipoNotificacion.SANTUARIO_ROTO;
            case HABITO_PERSONAL_MODIFICADO -> TipoNotificacion.HABITO_PERSONAL_MODIFICADO;
        };
    }
}
