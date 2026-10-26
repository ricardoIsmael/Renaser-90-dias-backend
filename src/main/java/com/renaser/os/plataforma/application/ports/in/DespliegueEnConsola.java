package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.plataforma.domain.model.despliegue.MetricasPorHora.MetricaDeLaHora;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/**
 * El despliegue de una funcionalidad como lo ve la consola (contrato fase 2 §3.4,
 * {@code GET /soporte-ti/funcionalidades/{clave}/despliegue}).
 *
 * @param enDespliegue {@code false} si llega a todos: entonces {@code porcentaje} es 100, {@code version} 0 y no hay
 *                     probadores ni autor
 * @param desplegable  {@code false} si se usa sin cuenta (en el catálogo no tiene roles, como {@code registro}): no se
 *                     puede empezar un despliegue y la app esconde el control
 * @param version      la que hay que mandar en el {@code PUT} (0 sin despliegue)
 * @param metricas     las últimas 24 h, de la hora más vieja a la actual, las horas sin datos en 0; también después de
 *                     terminar el despliegue (hasta que se purgan a las 48 h)
 */
public record DespliegueEnConsola(String clave, boolean enDespliegue, boolean desplegable, int porcentaje, long version,
                                  Instant iniciadoEn,
                                  FuncionalidadEnConsola.Actor actualizadoPor, Instant actualizadoEn,
                                  List<Probadora> probadores, List<MetricaDeLaHora> metricas) {

    /** @param nombre y @param email {@code null} si la cuenta ya no existe */
    public record Probadora(UserId id, String nombre, String email) {
    }
}
