package com.renaser.os.mentoring.domain.model.semaforo;

import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Lo que una vista de grupo muestra del semáforo de UN aprendiz.
 *
 * <p>Si el semáforo no lo mide —{@code SemaforoFinder} no trae su clave, por ejemplo porque todavía
 * no activó su programa— el aprendiz aparece igual, con {@link #SIN_MEDICION}: sin datos, sin
 * porcentaje, cero días con datos y ningún día. Un dato ausente nunca es cero ni verde (D-128,
 * D-131; docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md §1). La regla vive acá y en ningún otro lado:
 * la tabla del mentor, la del administrador y el resumen del líder la leen de este record.
 *
 * @param porcentaje   promedio de sus días medidos, 1 decimal; null si no tiene datos
 * @param diasConDatos cuántos días entraron a ese promedio
 * @param dias         los días de su ventana, del más viejo al más nuevo; vacío si no se mide
 */
public record MedicionDelAprendiz(BigDecimal porcentaje, ColorSemaforo color, int diasConDatos,
                                  List<DiaDelSemaforo> dias) {

    public static final MedicionDelAprendiz SIN_MEDICION =
            new MedicionDelAprendiz(null, ColorSemaforo.SIN_DATOS, 0, List.of());

    public MedicionDelAprendiz {
        Objects.requireNonNull(color, "color es obligatorio");
        dias = dias == null ? List.of() : List.copyOf(dias);
        if ((porcentaje == null) != (color == ColorSemaforo.SIN_DATOS)) {
            throw new IllegalArgumentException("Sin porcentaje es SIN_DATOS, y viceversa: " + porcentaje + "/" + color);
        }
    }

    /** @param ventana la que trajo el semáforo para este aprendiz; null si no se mide */
    public static MedicionDelAprendiz de(VentanaDelSemaforo ventana) {
        if (ventana == null) {
            return SIN_MEDICION;
        }
        return new MedicionDelAprendiz(ventana.porcentaje(), ventana.color(), ventana.diasConDatos(), ventana.dias());
    }
}
