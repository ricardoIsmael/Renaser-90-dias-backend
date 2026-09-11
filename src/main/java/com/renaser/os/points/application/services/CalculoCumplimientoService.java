package com.renaser.os.points.application.services;

import com.renaser.os.points.api.CalculoCumplimientoPort;
import com.renaser.os.points.api.EvaluacionCumplimiento;
import com.renaser.os.points.api.ObligacionEvidencia;
import com.renaser.os.points.api.VentanaEvaluacion;
import com.renaser.os.points.domain.model.cumplimiento.CalculoCumplimiento;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delegación fina hacia la función pura. No agrega lógica a propósito: si algo del cálculo
 * hiciera falta acá, sería señal de que se está bifurcando la fórmula.
 *
 * <p>Sin {@code @Transactional}: no toca la base.
 */
@Service
class CalculoCumplimientoService implements CalculoCumplimientoPort {

    @Override
    public EvaluacionCumplimiento evaluar(List<ObligacionEvidencia> obligaciones,
                                           Map<UUID, List<VentanaEvaluacion>> ventanasPorAprendiz) {
        return CalculoCumplimiento.evaluar(obligaciones, ventanasPorAprendiz);
    }
}
