package com.renaser.os.onboarding.domain.model.mapa;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * El conjunto de acciones motoras de UN aprendiz, con los limites del manual (§3 V06).
 *
 * <p>Existe como tipo propio, y no como una lista suelta en el servicio, porque estas dos reglas
 * dependen de OTRAS filas y por eso no pueden ser un {@code CHECK} de Postgres (regla 04):
 * <ul>
 *   <li><b>maximo {@value #MAX_TOTAL} acciones</b> en total — el manual avisa ademas por carga
 *       excesiva, pero seis es el techo duro;</li>
 *   <li><b>maximo {@value #MAX_POR_AREA} por area</b>, para que nadie cargue las seis sobre un
 *       objetivo y deje los otros dos sin sistema de ejecucion.</li>
 * </ul>
 *
 * <p><b>Estas reglas hoy solo viven en el telefono</b> (`reglas.ts`), o sea que un `curl` las
 * saltea. Esta clase es la que hace que el servidor deje de confiar en el cliente.
 */
public record AccionesDelMapa(List<AccionMapa> acciones) {

    public static final int MAX_TOTAL = 6;
    public static final int MAX_POR_AREA = 2;

    public AccionesDelMapa {
        Objects.requireNonNull(acciones, "acciones es obligatorio");
        requireDentroDelTecho(acciones);
        requireSinAccionIdRepetido(acciones);
        requireDentroDelTechoPorArea(acciones);
        acciones = List.copyOf(acciones);
    }

    public static AccionesDelMapa vacio() {
        return new AccionesDelMapa(List.of());
    }

    public int cantidad() {
        return acciones.size();
    }

    private static void requireDentroDelTecho(List<AccionMapa> acciones) {
        if (acciones.size() > MAX_TOTAL) {
            throw new IllegalArgumentException(
                    "El mapa admite hasta " + MAX_TOTAL + " acciones, recibidas: " + acciones.size());
        }
    }

    private static void requireDentroDelTechoPorArea(List<AccionMapa> acciones) {
        Map<AreaMapa, Integer> porArea = new EnumMap<>(AreaMapa.class);
        for (AccionMapa accion : acciones) {
            int cuantas = porArea.merge(accion.area(), 1, Integer::sum);
            if (cuantas > MAX_POR_AREA) {
                throw new IllegalArgumentException("El area " + accion.area().clave() + " admite hasta "
                        + MAX_POR_AREA + " acciones");
            }
        }
    }

    private static void requireSinAccionIdRepetido(List<AccionMapa> acciones) {
        long distintos = acciones.stream().map(AccionMapa::accionId).distinct().count();
        if (distintos != acciones.size()) {
            throw new IllegalArgumentException("Hay dos acciones con el mismo accionId");
        }
    }
}
