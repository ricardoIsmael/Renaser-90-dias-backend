package com.renaser.os.notifications.infrastructure.adapter.out.persistence.preferencia;

import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.notifications.infrastructure.adapter.out.persistence.notificacion.TipoNotificacionJpa;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/** Traduccion explicita caso a caso (nunca {@code valueOf} "magico" por nombre, aunque los
 * valores coincidan textualmente — mismo criterio que `support`/`points`, CLAUDE.MD §5.4.5). */
@Component
class PreferenciaNotificacionPersistenceMapper {

    PreferenciaNotificacion toDomain(PreferenciaNotificacionJpaEntity e) {
        return new PreferenciaNotificacion(UserId.of(e.getUsuarioId()), toDomainTipo(e.getTipo()), e.isHabilitada(),
                e.getActualizadoEn());
    }

    PreferenciaNotificacionJpaEntity toEntity(PreferenciaNotificacion p) {
        return new PreferenciaNotificacionJpaEntity(p.usuarioId().value(), toJpaTipo(p.tipo()), p.habilitada(),
                p.actualizadoEn());
    }

    TipoNotificacionJpa toJpaTipo(TipoNotificacion tipo) {
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
