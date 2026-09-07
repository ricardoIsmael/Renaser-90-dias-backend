package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Proyeccion del objetivo de 90 dias para la pantalla de Objetivos del Plan.
 *
 * <p>{@code meta}, {@code avance}, {@code unidad} y {@code porcentaje} son {@code null} cuando el
 * objetivo es puramente cualitativo. El cliente dibuja la barra de avance solo si vienen.
 *
 * <p>{@code porcentaje} viaja ya calculado y no se deja al cliente: es una regla de negocio
 * (incluido el tope de 100, ver {@link MetaCuantitativa#porcentaje()}) y si cada cliente la
 * recalcula, tarde o temprano dos pantallas muestran numeros distintos para el mismo dato.
 */
public record RocaMaestraResponse(UUID id, String eje, String objetivo, BigDecimal meta, BigDecimal avance,
                                   String unidad, Integer porcentaje, Instant creadoEn, Instant actualizadoEn) {

    public static RocaMaestraResponse from(RocaMaestra r) {
        MetaCuantitativa meta = r.meta();
        return new RocaMaestraResponse(r.id().value(), r.eje().name(), r.objetivo(),
                meta == null ? null : meta.objetivo(),
                meta == null ? null : meta.avance(),
                meta == null ? null : meta.unidad(),
                meta == null ? null : meta.porcentaje(),
                r.creadoEn(), r.actualizadoEn());
    }
}
