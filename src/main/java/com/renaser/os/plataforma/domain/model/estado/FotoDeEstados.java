package com.renaser.os.plataforma.domain.model.estado;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * La copia del estado que cada instancia tiene en memoria (plan.md D3): los desvíos de todas las funcionalidades y la
 * versión del último cambio. Es inmutable: una recarga arma otra y la reemplaza entera.
 */
public final class FotoDeEstados {

    private static final FotoDeEstados VACIA = new FotoDeEstados(0L, Map.of(), false);

    private final long version;
    private final Map<String, EstadoDeFuncionalidad> porClave;
    private final boolean leidaDeLaBase;

    private FotoDeEstados(long version, Map<String, EstadoDeFuncionalidad> porClave, boolean leidaDeLaBase) {
        this.version = version;
        this.porClave = porClave;
        this.leidaDeLaBase = leidaDeLaBase;
    }

    /** Antes de leer la base por primera vez: rige el valor por defecto del catálogo (spec, «Edge Cases»). */
    public static FotoDeEstados vacia() {
        return VACIA;
    }

    /** La versión es la mayor de las filas: sale de una secuencia global y ninguna fila se borra. */
    public static FotoDeEstados de(Collection<EstadoDeFuncionalidad> estados) {
        long version = estados.stream().mapToLong(EstadoDeFuncionalidad::version).max().orElse(0L);
        return new FotoDeEstados(version, estados.stream()
                .collect(Collectors.toUnmodifiableMap(EstadoDeFuncionalidad::clave, Function.identity())), true);
    }

    public long version() {
        return version;
    }

    public Optional<EstadoDeFuncionalidad> estadoDe(String clave) {
        return Optional.ofNullable(porClave.get(clave));
    }

    /** Si ya se leyó de la base alguna vez (si no, todo está en su valor por defecto). */
    public boolean leidaDeLaBase() {
        return leidaDeLaBase;
    }
}
