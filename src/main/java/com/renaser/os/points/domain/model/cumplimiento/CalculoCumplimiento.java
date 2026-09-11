package com.renaser.os.points.domain.model.cumplimiento;

import com.renaser.os.points.api.CumplimientoAprendiz;
import com.renaser.os.points.api.EstadoEvaluacion;
import com.renaser.os.points.api.EvaluacionCumplimiento;
import com.renaser.os.points.api.ObligacionEvidencia;
import com.renaser.os.points.api.VentanaEvaluacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La fórmula de cumplimiento de plan.md §8, pura y con un solo dueño.
 *
 * <p>Vive acá porque tanto la evaluación del mentor como el ranking entre grupos la usan, y
 * dos implementaciones que se separan con el tiempo es exactamente el fallo que el SDD
 * quiere evitar. El frontend no la recalcula: recibe el número ya hecho.
 *
 * <p>La decisión que más cambia el resultado es promediar porcentajes de alumnos en vez de
 * dividir totales. Con Ana 2/4 y Luis 3/3 el promedio da 75 % y el total 5/7 daría 71,43 %;
 * el segundo pondera a cada alumno por cuántas obligaciones tuvo, que no es lo que se quiere
 * medir de un mentor.
 */
public final class CalculoCumplimiento {

    /** Cambiar cualquier regla de acá obliga a subir esta versión y a recalcular a propósito. */
    public static final String VERSION = "2026-09-promedio-de-porcentajes";

    /** Escala interna. El redondeo de presentación es cosa de la UI, no del cálculo. */
    private static final int ESCALA = 4;

    private CalculoCumplimiento() {
    }

    /**
     * @param obligaciones obligaciones de evidencia de los alumnos considerados. Puede traer
     *                     varias filas de la misma obligación: se deduplican por
     *                     {@code obligacionId} quedándose con la primera entrega.
     * @param ventanasPorAprendiz tramos ya intersectados (mes ∩ pertenencia del alumno ∩
     *                            asignación del mentor). Un alumno con varios tramos en el
     *                            mismo mes los une antes de sacar su porcentaje (plan.md §8.5).
     */
    public static EvaluacionCumplimiento evaluar(List<ObligacionEvidencia> obligaciones,
                                                  Map<UUID, List<VentanaEvaluacion>> ventanasPorAprendiz) {
        if (ventanasPorAprendiz.isEmpty()) {
            return sinDatos(EstadoEvaluacion.SIN_HISTORIAL);
        }

        Map<UUID, ObligacionEvidencia> unicas = deduplicar(obligaciones);

        List<CumplimientoAprendiz> detalle = new ArrayList<>();
        for (Map.Entry<UUID, List<VentanaEvaluacion>> entrada : ventanasPorAprendiz.entrySet()) {
            detalle.add(cumplimientoDe(entrada.getKey(), entrada.getValue(), unicas.values()));
        }
        detalle.sort(Comparator.comparing(c -> c.aprendizId().toString()));

        List<CumplimientoAprendiz> evaluables = detalle.stream().filter(CumplimientoAprendiz::evaluable).toList();
        int entregadas = evaluables.stream().mapToInt(CumplimientoAprendiz::entregadas).sum();
        int esperadas = evaluables.stream().mapToInt(CumplimientoAprendiz::esperadas).sum();
        int tardias = detalle.stream().mapToInt(CumplimientoAprendiz::tardiasFueraDeVentana).sum();
        int verificadas = contarVerificadas(unicas.values(), ventanasPorAprendiz);

        if (evaluables.isEmpty()) {
            return new EvaluacionCumplimiento(null, 0, 0, 0, detalle.size(), tardias, verificadas,
                    EstadoEvaluacion.SIN_MUESTRA, VERSION, detalle);
        }

        BigDecimal promedio = evaluables.stream()
                .map(CumplimientoAprendiz::porcentaje)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(evaluables.size()), ESCALA, RoundingMode.HALF_UP);

        return new EvaluacionCumplimiento(promedio, entregadas, esperadas, evaluables.size(),
                detalle.size() - evaluables.size(), tardias, verificadas,
                EstadoEvaluacion.CALCULADA, VERSION, detalle);
    }

    /**
     * Una obligación reenviada tres veces sigue siendo una. Se conserva la entrega más
     * temprana: es la que acredita el cumplimiento, y quedarse con la última castigaría a
     * quien volvió a subir el archivo por un problema técnico.
     */
    private static Map<UUID, ObligacionEvidencia> deduplicar(List<ObligacionEvidencia> obligaciones) {
        Map<UUID, ObligacionEvidencia> unicas = new LinkedHashMap<>();
        for (ObligacionEvidencia obligacion : obligaciones) {
            unicas.merge(obligacion.obligacionId(), obligacion, CalculoCumplimiento::laMasTemprana);
        }
        return unicas;
    }

    private static ObligacionEvidencia laMasTemprana(ObligacionEvidencia uno, ObligacionEvidencia otro) {
        if (uno.entregadaEn() == null) {
            return otro;
        }
        if (otro.entregadaEn() == null) {
            return uno;
        }
        ObligacionEvidencia temprana = uno.entregadaEn().isAfter(otro.entregadaEn()) ? otro : uno;
        // La verificación de cualquiera de las filas cuenta: es la misma obligación.
        return temprana.verificada() || !(uno.verificada() || otro.verificada())
                ? temprana
                : new ObligacionEvidencia(temprana.obligacionId(), temprana.aprendizId(), temprana.venceEn(),
                        temprana.entregadaEn(), true);
    }

    private static CumplimientoAprendiz cumplimientoDe(UUID aprendizId, List<VentanaEvaluacion> ventanas,
                                                        Iterable<ObligacionEvidencia> obligaciones) {
        int esperadas = 0;
        int entregadas = 0;
        int tardias = 0;
        for (ObligacionEvidencia obligacion : obligaciones) {
            if (!obligacion.aprendizId().equals(aprendizId) || !dentro(ventanas, obligacion.venceEn())) {
                continue;
            }
            esperadas++;
            if (!obligacion.entregada()) {
                continue;
            }
            if (dentro(ventanas, obligacion.entregadaEn())) {
                entregadas++;
            } else {
                // Entregó, pero fuera del tramo de este mentor. Se muestra, no se acredita (P-05).
                tardias++;
            }
        }
        BigDecimal porcentaje = esperadas == 0
                ? null
                : BigDecimal.valueOf(entregadas)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(esperadas), ESCALA, RoundingMode.HALF_UP);
        return new CumplimientoAprendiz(aprendizId, entregadas, esperadas, tardias, porcentaje);
    }

    private static int contarVerificadas(Iterable<ObligacionEvidencia> obligaciones,
                                          Map<UUID, List<VentanaEvaluacion>> ventanasPorAprendiz) {
        int verificadas = 0;
        for (ObligacionEvidencia obligacion : obligaciones) {
            List<VentanaEvaluacion> ventanas = ventanasPorAprendiz.get(obligacion.aprendizId());
            if (ventanas != null && obligacion.verificada() && dentro(ventanas, obligacion.venceEn())) {
                verificadas++;
            }
        }
        return verificadas;
    }

    private static boolean dentro(List<VentanaEvaluacion> ventanas, Instant instante) {
        return ventanas.stream().anyMatch(v -> v.contiene(instante));
    }

    private static EvaluacionCumplimiento sinDatos(EstadoEvaluacion estado) {
        return new EvaluacionCumplimiento(null, 0, 0, 0, 0, 0, 0, estado, VERSION, List.of());
    }
}
