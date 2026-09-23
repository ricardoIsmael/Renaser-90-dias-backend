package com.renaser.os.rocks.domain.model.rocadiaria;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Una de las (hasta tres) acciones con las que se logra un objetivo diario.
 *
 * <p>Antes vivian colgando de la semana, en la tabla {@code acciones_criticas} (borrada vacia en
 * la V62). El
 * dueno describio la cadena al reves de como estaba construida: <i>"objetivo de los 90 dias, luego
 * mensual, luego semanal, y luego objetivo diario, y estos objetivos diarios tienen acciones para
 * hacerlo"</i>. Ahi cuelgan ahora.
 *
 * <p><b>No llevan tilde propio.</b> El objetivo del dia se cierra por evidencia y solo por evidencia
 * (R-02); darle a cada accion su propio "hecho" abriria un segundo camino para decir que algo esta
 * logrado sin evidencia detras. Las acciones describen COMO; el que se completa es el objetivo.
 */
public record AccionDiaria(int orden, String descripcion) {

    /** Pareto: si son mas de tres, ninguna es critica. Mismo tope que tenian las de la semana. */
    public static final int MAXIMO = 3;

    private static final int MAX_LEN = 500;

    public AccionDiaria {
        if (orden < 1 || orden > MAXIMO) {
            throw new IllegalArgumentException("orden debe estar entre 1 y " + MAXIMO + ": " + orden);
        }
        if (descripcion == null || descripcion.isBlank()) {
            throw new IllegalArgumentException("descripcion es obligatoria (accion " + orden + ")");
        }
        descripcion = descripcion.trim();
        if (descripcion.length() > MAX_LEN) {
            throw new IllegalArgumentException("descripcion supera " + MAX_LEN + " caracteres (accion " + orden + ")");
        }
    }

    /**
     * Valida la lista entera: de 0 a 3, con orden correlativo desde 1 y sin repetir.
     *
     * <p><b>Cero es valido</b> y es lo que la diferencia de las criticas de la semana, que exigian
     * exactamente tres: un objetivo del dia puede ser una sola cosa que no necesita desglose
     * ("pesarme en ayunas"). Obligar a inventar tres para poder guardar es lo que llevaba a escribir
     * relleno.
     *
     * <p>Lo que NO se acepta es un hueco: acciones con orden 1 y 3 pero sin la 2 se leen como "falta
     * una" en cualquier pantalla que las numere.
     */
    public static void requireListaValida(List<AccionDiaria> acciones) {
        if (acciones == null || acciones.isEmpty()) {
            return;
        }
        if (acciones.size() > MAXIMO) {
            throw new IllegalArgumentException("un objetivo diario admite hasta " + MAXIMO + " acciones");
        }
        Set<Integer> ordenes = acciones.stream().map(AccionDiaria::orden).collect(Collectors.toSet());
        Set<Integer> esperados = correlativosHasta(acciones.size());
        if (!ordenes.equals(esperados)) {
            throw new IllegalArgumentException("las acciones deben ir numeradas desde 1 y sin huecos: " + ordenes);
        }
    }

    private static Set<Integer> correlativosHasta(int cuantos) {
        return java.util.stream.IntStream.rangeClosed(1, cuantos).boxed().collect(Collectors.toSet());
    }
}
