package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamaestra;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class RocaMaestraPersistenceMapper {

    RocaMaestra toDomain(RocaMaestraJpaEntity e) {
        return RocaMaestra.rehydrate(RocaMaestraId.of(e.getId()), UserId.of(e.getParticipanteId()),
                toDomainEje(e.getEje()), e.getObjetivo(), toDomainMeta(e), e.getCreadoEn(), e.getActualizadoEn());
    }

    RocaMaestraJpaEntity toEntity(RocaMaestra r) {
        MetaCuantitativa meta = r.meta();
        return new RocaMaestraJpaEntity(r.id().value(), r.participanteId().value(), toJpaEje(r.eje()), r.objetivo(),
                meta == null ? null : meta.objetivo(),
                meta == null ? null : meta.avance(),
                meta == null ? null : meta.unidad(),
                meta == null ? null : meta.lineaBase(),
                r.creadoEn(), r.actualizadoEn());
    }

    /**
     * Las tres columnas van juntas o ninguna — lo garantiza el CHECK {@code roca_maestra_meta_completa}
     * de V35. Se comprueba igual sobre {@code meta}, que es la unica de las tres que no puede faltar
     * si hay meta: una fila a medias es un dato corrupto y es mejor que explote al leerla, en el
     * constructor de {@link MetaCuantitativa}, que arrastrarla silenciosamente hasta la pantalla.
     */
    private MetaCuantitativa toDomainMeta(RocaMaestraJpaEntity e) {
        if (e.getMeta() == null) {
            return null;
        }
        return new MetaCuantitativa(e.getMeta(), e.getAvance(), e.getUnidad(), e.getLineaBase());
    }

    EjeObjetivoJpa toJpaEje(EjeObjetivo eje) {
        return switch (eje) {
            case CUERPO -> EjeObjetivoJpa.CUERPO;
            case TRABAJO -> EjeObjetivoJpa.TRABAJO;
            case RELACIONES -> EjeObjetivoJpa.RELACIONES;
        };
    }

    EjeObjetivo toDomainEje(EjeObjetivoJpa jpa) {
        return switch (jpa) {
            case CUERPO -> EjeObjetivo.CUERPO;
            case TRABAJO -> EjeObjetivo.TRABAJO;
            case RELACIONES -> EjeObjetivo.RELACIONES;
        };
    }
}
