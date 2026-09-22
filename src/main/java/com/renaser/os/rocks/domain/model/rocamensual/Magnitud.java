package com.renaser.os.rocks.domain.model.rocamensual;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Como se comporta lo que un objetivo mide. Es lo UNICO que la calculadora del mes necesita saber
 * del objetivo: ni la unidad, ni el eje, ni el tipo de resultado le cambian una cuenta.
 *
 * <ul>
 *   <li>{@code NIVEL}: un valor que se mide en un momento dado y hay que mover (peso, deuda,
 *       facturacion mensual). El objetivo del mes es <b>donde hay que estar</b> al cierre.
 *   <li>{@code ACUMULADO}: lo que se suma a lo largo del programa (ventas acumuladas al dia 90).
 *       El objetivo del mes es <b>cuanto hay que sumar</b> en el mes.
 *   <li>{@code ESCALA}: un puntaje subjetivo del 1 al 10. Se reparte, pero redondeado a entero:
 *       "este mes tienes que estar en 6,3/10" es precision falsa.
 *   <li>{@code CLINICO}: un marcador de salud bajo tratamiento. El ritmo lo pone un profesional,
 *       no una app; aca no va ningun numero.
 * </ul>
 *
 * <p><b>Los valores de texto que se traducen aca los escribe el Mapa de Renacimiento</b>
 * (`map_health_result_type`, `map_business_result_type`, `map_business_period`) y viven en
 * `respuestas_onboarding`. Son los mismos que el cliente definio en el "Manual Tecnico de
 * Implementacion · Onboarding · Dia 7" §5.1; no se inventa ninguno. Un valor desconocido —una
 * version vieja de la app, un dato a mano— se trata como "todavia no eligio", que es la respuesta
 * honesta: sin saber que se mide no hay nada que repartir.
 */
public enum Magnitud {

    NIVEL,
    ACUMULADO,
    ESCALA,
    CLINICO;

    /** Periodo del Mapa que convierte una meta de negocio en una suma a juntar y no en un nivel. */
    private static final String PERIODO_ACUMULADO = "acumulado_dia_90";

    private static final Map<String, Magnitud> SALUD = Map.of(
            // Valores que se miden en un momento dado y hay que mover.
            "peso", NIVEL,
            "medidas", NIVEL,
            "fuerza", NIVEL,
            "resistencia", NIVEL,
            "sueno", NIVEL,
            // Un 1-10 es una percepcion. Se reparte, pero solo en enteros.
            "energia", ESCALA,
            // Marcar el ritmo de un indicador clinico es dosificar un tratamiento. El Mapa ya avisa
            // UNSAFE_HEALTH aca; inventar ademas una cuota mensual seria lo que ese aviso pide no hacer.
            "condicion_clinica", CLINICO,
            // Texto libre con unidad libre: se lee como nivel, que es lo que significan los dos
            // campos que la persona lleno ("parto de X, llego a Y").
            "otro", NIVEL);

    /** "/10", "puntos", "pts": alguien se invento una escala en el campo de unidad libre. */
    private static final Set<String> UNIDADES_DE_ESCALA = Set.of("/10", "/ 10", "10", "pt", "pts");

    /**
     * Que clase de magnitud es un resultado de salud. Vacio mientras no se eligio el tipo.
     *
     * <p>La unidad solo se mira cuando el tipo es {@code otro}: ahi el Mapa deja escribir lo que
     * sea, y quien puso "8" y "/10" esta declarando una escala aunque no la haya elegido.
     */
    public static Optional<Magnitud> deSalud(String tipoResultado, String unidad) {
        Magnitud magnitud = SALUD.get(normalizar(tipoResultado));
        if (magnitud == null) {
            return Optional.empty();
        }
        return Optional.of(magnitud == NIVEL && "otro".equals(normalizar(tipoResultado)) && pareceEscala(unidad)
                ? ESCALA
                : magnitud);
    }

    /**
     * Que clase de magnitud es un resultado de negocio. <b>Lo decide el periodo, no el tipo</b>: la
     * misma facturacion es un nivel que hay que alcanzar si se mide por mes o por semana ("llegar a
     * S/ 15 000 mensuales") y una suma que hay que juntar si se mide acumulada al dia 90 ("llevar
     * S/ 45 000 vendidos"). Es justo la pregunta que el Mapa hace en V04, asi que no hay que
     * adivinarla. Vacio mientras falte el tipo o el periodo — los dos bloquean el paso igual.
     */
    public static Optional<Magnitud> deNegocio(String tipoResultado, String periodo) {
        if (normalizar(tipoResultado).isEmpty() || normalizar(periodo).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(PERIODO_ACUMULADO.equals(normalizar(periodo)) ? ACUMULADO : NIVEL);
    }

    /** {@code true} cuando el numero del mes se muestra con decimales; una escala va entera. */
    public boolean admiteDecimales() {
        return this == NIVEL || this == ACUMULADO;
    }

    private static boolean pareceEscala(String unidad) {
        String u = normalizar(unidad);
        return UNIDADES_DE_ESCALA.contains(u) || u.startsWith("punto");
    }

    private static String normalizar(String valor) {
        return valor == null ? "" : valor.trim().toLowerCase();
    }
}
