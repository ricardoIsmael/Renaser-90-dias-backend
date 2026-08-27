package com.renaser.os.habits.infrastructure.adapter.out.persistence.guia;

import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuiaId;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.guia.SeccionGuia;
import com.renaser.os.habits.domain.model.guia.TipoMedioGuia;
import org.springframework.stereotype.Component;

@Component
class AdjuntoGuiaPersistenceMapper {

    AdjuntoGuia toDomain(AdjuntoGuiaJpaEntity e) {
        return AdjuntoGuia.rehydrate(AdjuntoGuiaId.of(e.getId()), GuiaHabitoId.of(e.getGuiaId()),
                toDomainSeccion(e.getSeccion()), toDomainTipoMedio(e.getTipoMedio()), e.getUrl(), e.getRutaStorage(),
                e.getMime(), e.getTamanoBytes(), e.getNombreOriginal(), e.getTitulo(), e.getOrden(), e.getCreadoEn(),
                e.getActualizadoEn());
    }

    AdjuntoGuiaJpaEntity toEntity(AdjuntoGuia a) {
        return new AdjuntoGuiaJpaEntity(a.id().value(), a.guiaId().value(), toJpaSeccion(a.seccion()),
                toJpaTipoMedio(a.tipoMedio()), a.url(), a.rutaStorage(), a.mime(), a.tamanoBytes(),
                a.nombreOriginal(), a.titulo(), (short) a.orden(), a.creadoEn(), a.actualizadoEn());
    }

    private SeccionGuiaJpa toJpaSeccion(SeccionGuia s) {
        return switch (s) {
            case QUE_HACER -> SeccionGuiaJpa.QUE_HACER;
            case COMO_HACERLO -> SeccionGuiaJpa.COMO_HACERLO;
            case CIENCIA -> SeccionGuiaJpa.CIENCIA;
            case RENASER -> SeccionGuiaJpa.RENASER;
            case ALQUIMIA -> SeccionGuiaJpa.ALQUIMIA;
            case RESULTADOS -> SeccionGuiaJpa.RESULTADOS;
            case COMO_VALIDAR -> SeccionGuiaJpa.COMO_VALIDAR;
        };
    }

    private SeccionGuia toDomainSeccion(SeccionGuiaJpa s) {
        return switch (s) {
            case QUE_HACER -> SeccionGuia.QUE_HACER;
            case COMO_HACERLO -> SeccionGuia.COMO_HACERLO;
            case CIENCIA -> SeccionGuia.CIENCIA;
            case RENASER -> SeccionGuia.RENASER;
            case ALQUIMIA -> SeccionGuia.ALQUIMIA;
            case RESULTADOS -> SeccionGuia.RESULTADOS;
            case COMO_VALIDAR -> SeccionGuia.COMO_VALIDAR;
        };
    }

    private TipoMedioGuiaJpa toJpaTipoMedio(TipoMedioGuia t) {
        return switch (t) {
            case ENLACE -> TipoMedioGuiaJpa.ENLACE;
            case IMAGEN -> TipoMedioGuiaJpa.IMAGEN;
            case AUDIO -> TipoMedioGuiaJpa.AUDIO;
        };
    }

    private TipoMedioGuia toDomainTipoMedio(TipoMedioGuiaJpa t) {
        return switch (t) {
            case ENLACE -> TipoMedioGuia.ENLACE;
            case IMAGEN -> TipoMedioGuia.IMAGEN;
            case AUDIO -> TipoMedioGuia.AUDIO;
        };
    }
}
