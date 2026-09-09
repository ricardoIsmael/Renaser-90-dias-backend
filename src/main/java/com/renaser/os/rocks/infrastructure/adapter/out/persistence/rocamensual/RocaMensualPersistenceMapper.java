package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocamensual;

import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensualId;
import org.springframework.stereotype.Component;

@Component
class RocaMensualPersistenceMapper {

    RocaMensual toDomain(RocaMensualJpaEntity e) {
        return RocaMensual.rehydrate(RocaMensualId.of(e.getId()), RocaMaestraId.of(e.getRocaMaestraId()),
                e.getNumeroMes(), e.getTitulo(), toDomainMeta(e), e.getCreadoEn(), e.getActualizadoEn());
    }

    RocaMensualJpaEntity toEntity(RocaMensual r) {
        MetaCuantitativa meta = r.meta();
        return new RocaMensualJpaEntity(r.id().value(), r.rocaMaestraId().value(), (short) r.numeroMes(),
                r.titulo(),
                meta == null ? null : meta.objetivo(),
                meta == null ? null : meta.avance(),
                meta == null ? null : meta.unidad(),
                r.creadoEn(), r.actualizadoEn());
    }

    /**
     * Las tres columnas van juntas o ninguna — lo garantiza el CHECK {@code roca_mensual_meta_completa}
     * de V36. Se comprueba igual sobre {@code meta}, que es la unica de las tres que no puede faltar
     * si hay meta: una fila a medias es un dato corrupto y es mejor que explote al leerla, en el
     * constructor de {@link MetaCuantitativa}, que arrastrarla silenciosamente hasta la pantalla.
     */
    private MetaCuantitativa toDomainMeta(RocaMensualJpaEntity e) {
        if (e.getMeta() == null) {
            return null;
        }
        // Sin linea base: `rocas_mensuales` no tiene esa columna (V43 solo toco `rocas_maestras`),
        // asi que el mes conserva la formula avance/meta. Ver E-166.
        return new MetaCuantitativa(e.getMeta(), e.getAvance(), e.getUnidad(), null);
    }
}
