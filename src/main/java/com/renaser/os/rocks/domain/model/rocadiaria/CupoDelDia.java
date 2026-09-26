package com.renaser.os.rocks.domain.model.rocadiaria;

import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Donde entra UNA accion mas en un dia ya planificado (D-177): la primera posicion libre de su eje.
 *
 * <p><b>No inventa topes.</b> Los dos que aplica ya existian: de 1 a 3 acciones por eje (la posicion
 * decide el color Pareto, {@link ColorPareto#paraPosicion}, y la base tiene
 * {@code CHECK (posicion BETWEEN 1 AND 3)}) y hasta 9 por dia ({@code CrearPlanDiarioCommand},
 * {@code @Size(max = 9)}). Con tres ejes, el segundo es consecuencia del primero; se verifica igual
 * para que el motivo del rechazo sea el verdadero.
 *
 * <p><b>Agregar nunca reordena.</b> Como el plan se guarda con posiciones contiguas desde 1
 * ({@code RocaDiariaService.requirePosicionesContiguasPorEje}), la primera libre es la siguiente a la
 * ultima: la accion nueva va DETRAS de las que el eje ya tiene y la VERDE sigue siendo la que la
 * persona eligio primero. Cambiar el orden es rehacer el dia.
 */
public final class CupoDelDia {

    public static final int MAXIMO_POR_EJE = 3;
    public static final int MAXIMO_POR_DIA = 9;

    private CupoDelDia() {
    }

    /**
     * @param delDia las acciones que ese dia ya tiene, de todos los ejes
     * @throws IllegalStateException {@code DAY_FULL} o {@code AXIS_FULL}, con esos prefijos
     */
    public static int siguientePosicion(List<RocaDiaria> delDia, EjeObjetivo eje) {
        if (delDia.size() >= MAXIMO_POR_DIA) {
            throw new IllegalStateException("DAY_FULL: ese dia ya tiene " + MAXIMO_POR_DIA + " acciones");
        }
        Set<Integer> ocupadas = delDia.stream().filter(roca -> roca.eje() == eje).map(RocaDiaria::posicion)
                .collect(Collectors.toSet());
        for (int posicion = 1; posicion <= MAXIMO_POR_EJE; posicion++) {
            if (!ocupadas.contains(posicion)) {
                return posicion;
            }
        }
        throw new IllegalStateException("AXIS_FULL: el eje " + eje + " ya tiene " + MAXIMO_POR_EJE + " acciones ese dia");
    }
}
