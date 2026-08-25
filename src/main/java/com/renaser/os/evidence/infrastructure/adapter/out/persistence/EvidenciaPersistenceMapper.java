package com.renaser.os.evidence.infrastructure.adapter.out.persistence;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class EvidenciaPersistenceMapper {

    Evidencia toDomain(EvidenciaJpaEntity e) {
        return Evidencia.rehydrate(EvidenciaId.of(e.getId()), UserId.of(e.getParticipanteId()), toDomainDestino(e),
                toDomainTipo(e.getTipo()), e.getBucket(), e.getRutaStorage(), e.getContenidoTexto(),
                e.getTimestampExif(), e.getSubidaEn(), e.getGpsLat(), e.getGpsLng(), e.isEsPrincipal(),
                toDomainEstado(e.getEstadoValidacion()), e.getNotasValidacion(), e.getIntentosIa(),
                e.isPenalizacionAplicada(), e.isPublicadaEnMuro(), e.getCreadoEn());
    }

    EvidenciaJpaEntity toEntity(Evidencia d) {
        EvidenciaJpaEntity e = new EvidenciaJpaEntity();
        e.setId(d.id().value());
        e.setParticipanteId(d.participanteId().value());
        aplicarDestino(e, d.destino());
        e.setTipo(toJpaTipo(d.tipo()));
        e.setBucket(d.bucket());
        e.setRutaStorage(d.rutaStorage());
        e.setContenidoTexto(d.contenidoTexto());
        e.setTimestampExif(d.timestampExif());
        e.setSubidaEn(d.subidaEn());
        e.setGpsLat(d.gpsLat());
        e.setGpsLng(d.gpsLng());
        e.setEsPrincipal(d.esPrincipal());
        e.setEstadoValidacion(toJpaEstado(d.estadoValidacion()));
        e.setNotasValidacion(d.notasValidacion());
        e.setIntentosIa((short) d.intentosIa());
        e.setPenalizacionAplicada(d.penalizacionAplicada());
        e.setPublicadaEnMuro(d.publicadaEnMuro());
        e.setCreadoEn(d.creadoEn());
        return e;
    }

    private void aplicarDestino(EvidenciaJpaEntity e, DestinoEvidencia destino) {
        switch (destino) {
            case DestinoEvidencia.RegistroHabito h -> e.setRegistroHabitoId(h.registroHabitoId());
            case DestinoEvidencia.RocaDiaria r -> e.setRocaDiariaId(r.rocaDiariaId());
            case DestinoEvidencia.RegistroEspiritu s -> e.setRegistroEspirituId(s.registroEspirituId());
        }
    }

    /** El arco exclusivo YA está garantizado por el CHECK de la base y por el dominio al crear —
     * acá solo se traduce lo que llegó, sin volver a validar cuál de los tres está presente. */
    private DestinoEvidencia toDomainDestino(EvidenciaJpaEntity e) {
        if (e.getRegistroHabitoId() != null) {
            return new DestinoEvidencia.RegistroHabito(e.getRegistroHabitoId());
        }
        if (e.getRocaDiariaId() != null) {
            return new DestinoEvidencia.RocaDiaria(e.getRocaDiariaId());
        }
        return new DestinoEvidencia.RegistroEspiritu(e.getRegistroEspirituId());
    }

    private TipoEvidenciaJpa toJpaTipo(TipoEvidencia tipo) {
        return switch (tipo) {
            case FOTO -> TipoEvidenciaJpa.FOTO;
            case VIDEO -> TipoEvidenciaJpa.VIDEO;
            case AUDIO -> TipoEvidenciaJpa.AUDIO;
            case TEXTO -> TipoEvidenciaJpa.TEXTO;
            case CAPTURA -> TipoEvidenciaJpa.CAPTURA;
        };
    }

    private TipoEvidencia toDomainTipo(TipoEvidenciaJpa jpa) {
        return switch (jpa) {
            case FOTO -> TipoEvidencia.FOTO;
            case VIDEO -> TipoEvidencia.VIDEO;
            case AUDIO -> TipoEvidencia.AUDIO;
            case TEXTO -> TipoEvidencia.TEXTO;
            case CAPTURA -> TipoEvidencia.CAPTURA;
        };
    }

    private EstadoValidacionJpa toJpaEstado(EstadoValidacion estado) {
        return switch (estado) {
            case PENDIENTE -> EstadoValidacionJpa.PENDIENTE;
            case VALIDA -> EstadoValidacionJpa.VALIDA;
            case RECHAZADA -> EstadoValidacionJpa.RECHAZADA;
            case REVISION_MANUAL -> EstadoValidacionJpa.REVISION_MANUAL;
            case ANULADA_ADMIN -> EstadoValidacionJpa.ANULADA_ADMIN;
        };
    }

    private EstadoValidacion toDomainEstado(EstadoValidacionJpa jpa) {
        return switch (jpa) {
            case PENDIENTE -> EstadoValidacion.PENDIENTE;
            case VALIDA -> EstadoValidacion.VALIDA;
            case RECHAZADA -> EstadoValidacion.RECHAZADA;
            case REVISION_MANUAL -> EstadoValidacion.REVISION_MANUAL;
            case ANULADA_ADMIN -> EstadoValidacion.ANULADA_ADMIN;
        };
    }
}
