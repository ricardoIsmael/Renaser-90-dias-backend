package com.renaser.os.habits.infrastructure.adapter.out.persistence.santuario;

import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.santuario.EstadoSesionBloqueo;
import com.renaser.os.habits.domain.model.santuario.MotivoSalidaBloqueo;
import com.renaser.os.habits.domain.model.santuario.SesionBloqueo;
import org.springframework.stereotype.Component;

@Component
class SesionBloqueoPersistenceMapper {

    SesionBloqueo toDomain(SesionBloqueoJpaEntity e) {
        return SesionBloqueo.rehydrate(RegistroHabitoId.of(e.getRegistroHabitoId()), toDomainEstado(e.getEstado()),
                e.getIniciadaEn(), e.getTerminadaEn(), e.getDuracionMinimaMin(), toDomainMotivo(e.getMotivoSalida()),
                e.getEvidenciaSalidaBucket(), e.getEvidenciaSalidaRuta(), e.isPenalizacionAplicada(), e.getCreadoEn(),
                e.getActualizadoEn());
    }

    SesionBloqueoJpaEntity toEntity(SesionBloqueo s) {
        return new SesionBloqueoJpaEntity(s.registroHabitoId().value(), toJpaEstado(s.estado()), s.iniciadaEn(),
                s.terminadaEn(), (short) s.duracionMinimaMin(), toJpaMotivo(s.motivoSalida()),
                s.evidenciaSalidaBucket(), s.evidenciaSalidaRuta(), s.penalizacionAplicada(), s.creadoEn(),
                s.actualizadoEn());
    }

    private EstadoSesionBloqueoJpa toJpaEstado(EstadoSesionBloqueo estado) {
        return switch (estado) {
            case ACTIVA -> EstadoSesionBloqueoJpa.ACTIVA;
            case COMPLETADA -> EstadoSesionBloqueoJpa.COMPLETADA;
            case ROTA -> EstadoSesionBloqueoJpa.ROTA;
            case CANCELADA -> EstadoSesionBloqueoJpa.CANCELADA;
        };
    }

    private EstadoSesionBloqueo toDomainEstado(EstadoSesionBloqueoJpa jpa) {
        return switch (jpa) {
            case ACTIVA -> EstadoSesionBloqueo.ACTIVA;
            case COMPLETADA -> EstadoSesionBloqueo.COMPLETADA;
            case ROTA -> EstadoSesionBloqueo.ROTA;
            case CANCELADA -> EstadoSesionBloqueo.CANCELADA;
        };
    }

    private MotivoSalidaBloqueoJpa toJpaMotivo(MotivoSalidaBloqueo motivo) {
        if (motivo == null) {
            return null;
        }
        return switch (motivo) {
            case SALIDA_TEMPRANA -> MotivoSalidaBloqueoJpa.SALIDA_TEMPRANA;
            case VIOLACION_APP_USADA -> MotivoSalidaBloqueoJpa.VIOLACION_APP_USADA;
            case MANUAL -> MotivoSalidaBloqueoJpa.MANUAL;
        };
    }

    private MotivoSalidaBloqueo toDomainMotivo(MotivoSalidaBloqueoJpa jpa) {
        if (jpa == null) {
            return null;
        }
        return switch (jpa) {
            case SALIDA_TEMPRANA -> MotivoSalidaBloqueo.SALIDA_TEMPRANA;
            case VIOLACION_APP_USADA -> MotivoSalidaBloqueo.VIOLACION_APP_USADA;
            case MANUAL -> MotivoSalidaBloqueo.MANUAL;
        };
    }
}
