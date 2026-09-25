package com.renaser.os.mentoring.domain.model.semaforo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * El semáforo de un grupo en dos números: cuántos aprendices en cada color y el promedio de los que
 * tienen datos. Es lo que ve el líder de cada grupo (sin nombres, RL-07 del SDD 002) y, sin el
 * promedio, el encabezado de la tabla del mentor. Los dos salen de acá para que el líder y el
 * mentor nunca vean cifras distintas del mismo grupo.
 *
 * @param promedio promedio de los porcentajes de los aprendices CON datos, 1 decimal, mitad hacia
 *                 arriba; null si ninguno tiene datos. Nunca cero por falta de datos (D-128).
 */
public record ResumenDelGrupo(ConteoPorColor conteo, BigDecimal promedio) {

    private static final int DECIMALES = 1;

    public ResumenDelGrupo {
        Objects.requireNonNull(conteo, "conteo es obligatorio");
    }

    public static ResumenDelGrupo de(Collection<MedicionDelAprendiz> mediciones) {
        ConteoPorColor conteo = ConteoPorColor.de(mediciones.stream().map(MedicionDelAprendiz::color).toList());
        return new ResumenDelGrupo(conteo, promedioDe(mediciones));
    }

    private static BigDecimal promedioDe(Collection<MedicionDelAprendiz> mediciones) {
        List<BigDecimal> conDatos = mediciones.stream()
                .map(MedicionDelAprendiz::porcentaje)
                .filter(Objects::nonNull)
                .toList();
        if (conDatos.isEmpty()) {
            return null;
        }
        BigDecimal suma = conDatos.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return suma.divide(BigDecimal.valueOf(conDatos.size()), DECIMALES, RoundingMode.HALF_UP);
    }
}
