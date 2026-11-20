package com.renaser.os.mentoring.domain.model.quesigue;

import java.math.BigDecimal;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * «Qué sigue» y «Quién baja el promedio» de «Mi evaluación» (spec 057, FR-011 y FR-012). Reglas fijas y puras: el servidor decide qué
 * le conviene hacer al mentor y la app solo lo pinta. Sin Spring, sin reloj, sin base de datos.
 */
public final class QueSigue {

    /** Por debajo de este porcentaje un aprendiz baja el promedio del mentor y entra a «Quién baja». */
    public static final BigDecimal UMBRAL_QUE_BAJA = BigDecimal.valueOf(80);
    /** Por debajo de este porcentaje el aprendiz está en rojo y merece un «Escríbele». */
    public static final BigDecimal UMBRAL_ROJO = BigDecimal.valueOf(60);
    /** Cuántos puntos tiene que bajar el mentor respecto del mes anterior para sugerirle conversar con su grupo. */
    public static final int PUNTOS_DE_CAIDA = 5;

    static final int MAXIMO_QUIEN_BAJA = 5;
    static final int MAXIMO_ESCRIBIR = 2;
    static final int MAXIMO_ACCIONES = 3;
    private static final Locale ESPANOL_PERU = Locale.forLanguageTag("es-PE");

    private QueSigue() {
    }

    /** Un aprendiz que el mentor acompaña hoy, con lo que lleva en el mes. */
    public record Candidato(UUID aprendizId, UUID grupoId, String nombre, BigDecimal porcentaje, int entregadas, int esperadas) {
    }

    public record AprendizQueBaja(UUID aprendizId, UUID grupoId, String nombre, BigDecimal porcentaje, int entregadas, int esperadas) {
    }

    /** @param aprendizId a quién se refiere; solo en {@code ESCRIBIR} (con su {@code grupoId}), para abrir su conversación; null en las demás */
    public record Accion(Tipo tipo, String titulo, String motivo, UUID aprendizId, UUID grupoId) {
    }

    public enum Tipo { ESCRIBIR, REVISAR_GRUPO, VENTANAS, RECONOCER }

    /**
     * Los que acompaña hoy y llevan menos de 80 % del mes, del menor al mayor, como mucho 5. Quien no tiene nada esperado no se evalúa.
     */
    public static List<AprendizQueBaja> quienBaja(List<Candidato> candidatos) {
        return candidatos.stream()
                .filter(c -> c.esperadas() > 0 && c.porcentaje() != null && c.porcentaje().compareTo(UMBRAL_QUE_BAJA) < 0)
                .sorted(Comparator.comparing(Candidato::porcentaje).thenComparing(Candidato::nombre, Comparator.nullsLast(Comparator.naturalOrder())))
                .limit(MAXIMO_QUIEN_BAJA)
                .map(c -> new AprendizQueBaja(c.aprendizId(), c.grupoId(), c.nombre(), c.porcentaje(), c.entregadas(), c.esperadas()))
                .toList();
    }

    /**
     * Hasta 3 acciones, en este orden: escribirle a quien está en rojo (hasta 2), conversar con el grupo si el porcentaje bajó 5 puntos o más,
     * recordar las horas de entrega si hubo evidencias fuera de hora y, solo si no salió ninguna y va en 80 o más, reconocer el avance.
     *
     * @param porcentaje         el del mes; null si el mes no está calculado
     * @param porcentajeAnterior el del mes anterior; null si ese mes no está calculado
     */
    public static List<Accion> acciones(BigDecimal porcentaje, BigDecimal porcentajeAnterior, YearMonth mesAnterior,
                                        int tardiasFueraDeVentana, List<AprendizQueBaja> quienBaja) {
        if (porcentaje == null) {
            return List.of();
        }
        List<Accion> acciones = new ArrayList<>();
        quienBaja.stream().filter(a -> a.porcentaje().compareTo(UMBRAL_ROJO) < 0).limit(MAXIMO_ESCRIBIR)
                .forEach(a -> acciones.add(escribir(a)));
        if (porcentajeAnterior != null && porcentajeAnterior.subtract(porcentaje).compareTo(BigDecimal.valueOf(PUNTOS_DE_CAIDA)) >= 0) {
            acciones.add(new Accion(Tipo.REVISAR_GRUPO, "Conversa con tu grupo sobre qué cambió",
                    "Bajaste " + puntos(porcentajeAnterior.subtract(porcentaje)) + " desde " + nombreDelMes(mesAnterior.getMonth()), null, null));
        }
        if (tardiasFueraDeVentana > 0) {
            acciones.add(new Accion(Tipo.VENTANAS, "Recuérdales las horas de entrega", tardiasFueraDeVentana == 1
                    ? "1 evidencia llegó fuera de hora" : tardiasFueraDeVentana + " evidencias llegaron fuera de hora", null, null));
        }
        if (acciones.isEmpty() && porcentaje.compareTo(UMBRAL_QUE_BAJA) >= 0) {
            acciones.add(new Accion(Tipo.RECONOCER, "Reconoce el avance de tu grupo", "Van en verde este mes", null, null));
        }
        return acciones.stream().limit(MAXIMO_ACCIONES).toList();
    }

    private static Accion escribir(AprendizQueBaja a) {
        return new Accion(Tipo.ESCRIBIR, "Escríbele a " + a.nombre(), "Entregó " + a.entregadas() + " de " + a.esperadas() + " este mes",
                a.aprendizId(), a.grupoId());
    }

    /** «5 puntos», «1 punto»: la diferencia redondeada; nunca menos de 1 porque quien llega aquí bajó 5 o más. */
    private static String puntos(BigDecimal diferencia) {
        long n = diferencia.setScale(0, java.math.RoundingMode.HALF_UP).longValue();
        return n == 1 ? "1 punto" : n + " puntos";
    }

    /** «octubre»: el mes en español, en minúscula, para decirlo dentro de una frase. */
    public static String nombreDelMes(Month mes) {
        return mes.getDisplayName(TextStyle.FULL, ESPANOL_PERU).toLowerCase(ESPANOL_PERU);
    }
}
