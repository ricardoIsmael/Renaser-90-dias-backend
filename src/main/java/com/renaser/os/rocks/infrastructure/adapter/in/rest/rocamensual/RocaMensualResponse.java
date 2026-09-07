package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamensual;

import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Proyeccion del tramo mensual para la pantalla de Objetivos del Plan.
 *
 * <p>Viaja {@code rocaMaestraId} y no el eje porque el agregado no lo tiene: la Roca Mensual
 * cuelga de la maestra (V36) y el eje es un dato de esa otra fila. El cliente ya pide
 * {@code GET /api/v1/rocks/master} para dibujar la misma pantalla, asi que tiene con que cruzarlos
 * — cargar aca el eje obligaria a un join que hoy nadie necesita.
 *
 * <p>{@code meta}, {@code avance}, {@code unidad} y {@code porcentaje} son {@code null} cuando el
 * tramo es puramente cualitativo. El cliente dibuja la barra de avance solo si vienen.
 *
 * <p>{@code porcentaje} viaja ya calculado y no se deja al cliente: es una regla de negocio
 * (incluido el tope de 100, ver {@link MetaCuantitativa#porcentaje()}) y si cada cliente la
 * recalcula, tarde o temprano dos pantallas muestran numeros distintos para el mismo dato. Lo
 * mismo vale para {@code diaDeCierre}: que el mes 2 cierre al dia 60 lo decide el dominio.
 */
public record RocaMensualResponse(UUID id, UUID rocaMaestraId, int numeroMes, int diaDeCierre, String titulo,
                                   BigDecimal meta, BigDecimal avance, String unidad, Integer porcentaje,
                                   Instant creadoEn, Instant actualizadoEn) {

    public static RocaMensualResponse from(RocaMensual r) {
        MetaCuantitativa meta = r.meta();
        return new RocaMensualResponse(r.id().value(), r.rocaMaestraId().value(), r.numeroMes(), r.diaDeCierre(),
                r.titulo(),
                meta == null ? null : meta.objetivo(),
                meta == null ? null : meta.avance(),
                meta == null ? null : meta.unidad(),
                meta == null ? null : meta.porcentaje(),
                r.creadoEn(), r.actualizadoEn());
    }
}
