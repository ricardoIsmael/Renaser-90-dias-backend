package com.renaser.os.rocks.application.services;

import com.renaser.os.points.api.PorcentajeRocasFinder;
import com.renaser.os.rocks.application.ports.out.coherencia.CargarConteoDiarioRocasPort;
import com.renaser.os.rocks.domain.model.coherencia.DiaRocas;
import com.renaser.os.rocks.domain.model.coherencia.PorcentajeRocas;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Implementación EN LOTE (D-43) de {@link PorcentajeRocasFinder}. Una única
 * consulta agregada ({@link CargarConteoDiarioRocasPort}) trae los conteos
 * crudos por día de TODOS los participantes pedidos; el cálculo en sí (doble
 * redondeo, ventana vacía → 100) es dominio puro
 * ({@link PorcentajeRocas#calcular(List)}), testeable sin Postgres — ese es
 * el punto de D-43: que la fórmula sea testeable sin Postgres, no solo que la
 * consulta sea rápida.
 */
@Service
class PorcentajeRocasService implements PorcentajeRocasFinder {

    /** Ventana de Ley VI: 7 días UTC cerrados, terminando (incluido) en {@code hasta}. */
    private static final long DIAS_VENTANA = 7L;

    private final CargarConteoDiarioRocasPort cargarConteoDiarioRocasPort;

    PorcentajeRocasService(CargarConteoDiarioRocasPort cargarConteoDiarioRocasPort) {
        this.cargarConteoDiarioRocasPort = cargarConteoDiarioRocasPort;
    }

    @Override
    public Map<UserId, BigDecimal> porcentajePorParticipante(Collection<UserId> participantes, LocalDate hasta) {
        if (participantes == null || participantes.isEmpty()) {
            return Map.of();
        }
        LocalDate desde = hasta.minusDays(DIAS_VENTANA - 1);
        Map<UserId, List<DiaRocas>> conteosPorParticipante =
                cargarConteoDiarioRocasPort.conteoDiarioPorParticipante(participantes, desde, hasta);

        Map<UserId, BigDecimal> resultado = new LinkedHashMap<>();
        for (UserId participante : participantes) {
            List<DiaRocas> dias = conteosPorParticipante.getOrDefault(participante, List.of());
            resultado.put(participante, PorcentajeRocas.calcular(dias));
        }
        return resultado;
    }
}
