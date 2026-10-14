package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.DespliegueEnConsola;
import com.renaser.os.plataforma.domain.model.despliegue.MetricasPorHora.Conteo;

import java.time.Instant;
import java.util.List;

/**
 * {@code GET /soporte-ti/funcionalidades/{clave}/despliegue} (contrato fase 2 §3.4): el escalón, quién lo movió, los
 * probadores y las últimas 24 h por hora (las horas sin datos en 0).
 */
record DespliegueResponse(String clave, boolean enDespliegue, int porcentaje, long version, Instant iniciadoEn,
                          FuncionalidadEnConsolaResponse.ActorResponse actualizadoPor, Instant actualizadoEn,
                          List<ProbadoraResponse> probadores, List<MetricaResponse> metricas) {

    static DespliegueResponse from(DespliegueEnConsola d) {
        return new DespliegueResponse(d.clave(), d.enDespliegue(), d.porcentaje(), d.version(), d.iniciadoEn(),
                FuncionalidadEnConsolaResponse.ActorResponse.from(d.actualizadoPor()), d.actualizadoEn(),
                d.probadores().stream().map(p -> new ProbadoraResponse(p.id().toString(), p.nombre(), p.email()))
                        .toList(),
                d.metricas().stream().map(m -> new MetricaResponse(m.hora(), ConteoResponse.from(m.dentro()),
                        ConteoResponse.from(m.fuera()))).toList());
    }

    record ProbadoraResponse(String id, String nombre, String email) {
    }

    record MetricaResponse(Instant hora, ConteoResponse dentro, ConteoResponse fuera) {
    }

    record ConteoResponse(long pedidos, long errores) {

        static ConteoResponse from(Conteo conteo) {
            return new ConteoResponse(conteo.pedidos(), conteo.errores());
        }
    }
}
