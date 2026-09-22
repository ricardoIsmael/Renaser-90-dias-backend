package com.renaser.os.rocks.domain.model.rocamensual;

import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;

import java.math.BigDecimal;

/**
 * Lo poco que la calculadora necesita de un eje, ya traducido desde donde vive cada cosa.
 *
 * <p>Existe porque las tres piezas <b>no estan juntas en ninguna parte</b>: la Roca Maestra guarda
 * el numero, la unidad y la linea base, pero no que se mide; eso lo declaro la persona en el Mapa.
 * Y Relaciones es el caso extremo: su objetivo viaja a {@code rocas_maestras} sin meta cuantitativa
 * a proposito, asi que sus dos numeros salen enteros del Mapa.
 *
 * <p>Una fabrica por eje en vez de un {@code switch} con seis parametros sueltos: cada eje aporta
 * cosas distintas y el compilador es quien tiene que impedir mezclarlas.
 */
public record DatosDelEje(BigDecimal lineaBase, BigDecimal valorHoy, BigDecimal meta, Magnitud magnitud,
                           BigDecimal topePorMes, String unidad) {

    /** La escala del 1 al 10 con la que el Mapa ya dibuja los hitos de Relaciones. */
    public static final String UNIDAD_ESCALA = "/10";

    /**
     * Cuerpo. El unico eje con tope absoluto, y solo cuando lo que se mide es el peso.
     *
     * @param unidadDelMapa {@code map_health_unit}. Solo se usa para detectar una escala cuando el
     *                      tipo de resultado es {@code otro}; la unidad que se muestra sale de la
     *                      meta, que es la que la persona confirmo al sellar el objetivo.
     */
    public static DatosDelEje deSalud(MetaCuantitativa meta, String tipoResultado, String unidadDelMapa) {
        BigDecimal referencia = referenciaDe(meta);
        return new DatosDelEje(lineaBaseDe(meta), avanceDe(meta), objetivoDe(meta),
                Magnitud.deSalud(tipoResultado, unidadDelMapa).orElse(null),
                TopeSaludableDelMes.deSalud(tipoResultado, referencia), unidadDe(meta));
    }

    /** Trabajo. Sin tope absoluto: nadie sabe si 2 000 soles al mes son mucho o poco. */
    public static DatosDelEje deNegocio(MetaCuantitativa meta, String tipoResultado, String periodo) {
        return new DatosDelEje(lineaBaseDe(meta), avanceDe(meta), objetivoDe(meta),
                Magnitud.deNegocio(tipoResultado, periodo).orElse(null), null, unidadDe(meta));
    }

    /**
     * Relaciones. Los dos numeros salen del Mapa y <b>no hay medicion intermedia</b>: nadie vuelve a
     * puntuar el vinculo cada semana, asi que el valor de hoy es siempre el punto de partida. La
     * consecuencia es deseable — el objetivo mensual de Relaciones es exactamente el hito que el
     * Mapa dibuja, y no se mueve solo.
     */
    public static DatosDelEje deRelaciones(Integer situacionActual, Integer resultadoDia90) {
        BigDecimal base = situacionActual == null ? null : BigDecimal.valueOf(situacionActual);
        BigDecimal meta = resultadoDia90 == null ? null : BigDecimal.valueOf(resultadoDia90);
        return new DatosDelEje(base, base, meta, Magnitud.ESCALA, null, UNIDAD_ESCALA);
    }

    /** El objetivo de ese mes, visto desde el mes en curso. Ver {@link CalculadoraObjetivoMensual}. */
    public ObjetivoDelMes calcular(int numeroMes, int mesActual) {
        return CalculadoraObjetivoMensual.calcular(numeroMes, mesActual, lineaBase, valorHoy, meta, magnitud,
                topePorMes);
    }

    /**
     * El punto de partida. Una meta <b>sin</b> linea base es la forma vieja de {@code MetaCuantitativa}
     * ({@code nueva()}, y las filas anteriores a la V43): significa "se arranca en cero y mas es
     * mejor", asi que el cero es el punto de partida real y no un dato faltante.
     */
    private static BigDecimal lineaBaseDe(MetaCuantitativa meta) {
        if (meta == null) {
            return null;
        }
        return meta.lineaBase() == null ? BigDecimal.ZERO : meta.lineaBase();
    }

    private static BigDecimal avanceDe(MetaCuantitativa meta) {
        return meta == null ? null : meta.avance();
    }

    private static BigDecimal objetivoDe(MetaCuantitativa meta) {
        return meta == null ? null : meta.objetivo();
    }

    /** El peso de hoy, o de donde partio si todavia no volvio a pesarse. */
    private static BigDecimal referenciaDe(MetaCuantitativa meta) {
        BigDecimal avance = avanceDe(meta);
        return avance != null ? avance : lineaBaseDe(meta);
    }

    private static String unidadDe(MetaCuantitativa meta) {
        return meta == null ? "" : meta.unidad();
    }
}
