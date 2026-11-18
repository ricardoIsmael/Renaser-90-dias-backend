package com.renaser.os.leadership.infrastructure.adapter.out.documento;

import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.shared.domain.TituloEnTipoOracion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Locale;
import java.util.Map;

/**
 * Las palabras del documento: fechas en español de Perú y las etiquetas de cada estado. Las fechas se escriben con tablas propias y no con
 * {@code DateTimeFormatter}: así el documento sale igual en cualquier JDK y no depende de los datos de idioma de la máquina.
 *
 * <p>Un estado nunca se dice solo con color: cada uno lleva su palabra y su símbolo ({@link #simbolo}), como en las pantallas del líder
 * (spec 056, FR-043).
 */
final class Textos {

    private static final String[] MESES = {"enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
            "noviembre", "diciembre"};
    private static final String[] MESES_CORTOS = {"ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sep", "oct", "nov", "dic"};
    private static final String[] DIAS = {"lunes", "martes", "miércoles", "jueves", "viernes", "sábado", "domingo"};
    private static final String[] DIAS_CORTOS = {"lun", "mar", "mié", "jue", "vie", "sáb", "dom"};

    private static final Map<String, String> TIPOS_DE_EVIDENCIA = Map.of("FOTO", "Foto", "VIDEO", "Video", "AUDIO", "Audio", "TEXTO", "Texto",
            "CAPTURA", "Captura");
    private static final Map<String, String> REVISIONES = Map.of("VALIDA", "Verificada", "RECHAZADA", "Rechazada",
            "REVISION_MANUAL", "En revisión", "ANULADA_ADMIN", "Anulada");
    private static final Map<String, String> MOTIVOS = Map.of("SIN_NADA_PLANIFICADO", "Sin nada planificado", "NO_ACTIVADO",
            "Programa sin activar", "PENDIENTE_DE_CALCULO", "Pendiente de cálculo", "CUENTA_SUSPENDIDA", "Cuenta suspendida", "PAUSADO",
            "Semáforo en pausa", "FUERA_DEL_PROGRAMA", "Fuera del programa", "EN_CURSO", "Día en curso");
    private static final Map<String, String> TENDENCIAS = Map.of("SUBE", "Sube ↑", "BAJA", "Baja ↓", "IGUAL", "Igual =");

    private Textos() {
    }

    /** {@code 14 de noviembre de 2026}. */
    static String fechaLarga(LocalDate fecha) {
        return fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }

    /** {@code sábado 14 de noviembre}. */
    static String fechaConDia(LocalDate fecha) {
        return DIAS[fecha.getDayOfWeek().getValue() - 1] + " " + fecha.getDayOfMonth() + " de " + MESES[fecha.getMonthValue() - 1];
    }

    /** {@code 14 nov}. */
    static String fechaCorta(LocalDate fecha) {
        return fecha.getDayOfMonth() + " " + MESES_CORTOS[fecha.getMonthValue() - 1];
    }

    /** {@code sáb 14 nov}. */
    static String diaCorto(LocalDate fecha) {
        return DIAS_CORTOS[fecha.getDayOfWeek().getValue() - 1] + " " + fechaCorta(fecha);
    }

    /** {@code noviembre de 2026}. */
    static String mes(LocalDate fecha) {
        return MESES[fecha.getMonthValue() - 1] + " de " + fecha.getYear();
    }

    /** {@code 17 de noviembre de 2026, 22:00}: el instante en la zona de la persona, no en la del servidor. */
    static String generado(Instant instante, String zona) {
        ZonedDateTime local = instante.atZone(ZoneId.of(zona));
        return fechaLarga(local.toLocalDate()) + String.format(Locale.ROOT, ", %02d:%02d", local.getHour(), local.getMinute());
    }

    /** {@code 66.7 %}: un decimal solo si lo hay; el punto decimal es el del español de Perú. */
    static String porcentaje(BigDecimal valor) {
        BigDecimal limpio = valor.signum() == 0 ? BigDecimal.ZERO : valor.stripTrailingZeros();
        return limpio.toPlainString() + " %";
    }

    /**
     * {@code 83 %}: la evaluación del mes de un mentor, entera y redondeada, como la dice la app (RL-23: se redondea solo al mostrar). El valor ya viene en
     * escala de 0 a 100 —{@code 0.1323} es el 0,13 %, no el 13,23 %—, así que no se multiplica: solo se redondea.
     */
    static String evaluacion(BigDecimal valor) {
        return valor.setScale(0, RoundingMode.HALF_UP).toPlainString() + " %";
    }

    /** {@code 1 consulta}, {@code 2 consultas}: el número con su palabra en singular o en plural. */
    static String cantidad(int cantidad, String singular, String plural) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }

    /** El nombre de un hábito del catálogo, que llega en MAYÚSCULAS, en tipo oración como en la app; sin escapar (quien lo escribe en el HTML lo escapa). */
    static String habito(String titulo) {
        return TituloEnTipoOracion.de(titulo);
    }

    static String simbolo(ColorDeLaFila estado) {
        return switch (estado) {
            case ROJO -> "×";
            case AMARILLO -> "!";
            case VERDE -> "✓";
            case SIN_DATOS -> "·";
            case NO_SE_MIDE -> "–";
        };
    }

    /** La clase de color del estado, solo para pintar: el significado lo dicen la palabra y el símbolo. */
    static String clase(ColorDeLaFila estado) {
        return "estado-" + estado.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static String tipoDeEvidencia(String tipo) {
        return TIPOS_DE_EVIDENCIA.getOrDefault(tipo, "Evidencia");
    }

    /** La revisión de una evidencia en palabras; lo que todavía nadie revisó es «Pendiente de revisión». */
    static String revision(String estado) {
        return REVISIONES.getOrDefault(estado == null ? "" : estado, "Pendiente de revisión");
    }

    static String motivo(String motivo) {
        return MOTIVOS.getOrDefault(motivo, "Sin medir");
    }

    static String tendencia(String tendencia) {
        return TENDENCIAS.getOrDefault(tendencia, "Sin comparación");
    }

    /** El estado de un hábito en una palabra: lo vencido y lo pendiente se dicen distinto, como en la ficha del mentor. */
    static String estadoDelHabito(String estado) {
        return switch (estado == null ? "" : estado) {
            case "COMPLETADO" -> "Cumplido";
            case "FALLIDO", "EXPIRADO" -> "Vencido";
            default -> "Pendiente";
        };
    }

    /** Cómo va la entrega de la evidencia de un hábito; vacío si el hábito no pide evidencia. */
    static String entrega(String entrega) {
        return switch (entrega == null ? "" : entrega) {
            case "ENTREGADA" -> "Evidencia entregada";
            case "SIN_ENTREGA" -> "Evidencia sin entregar";
            default -> "";
        };
    }

    static String tipoDeObservacion(String tipo) {
        return switch (tipo == null ? "" : tipo) {
            case "RECONOCIMIENTO" -> "Reconocimiento";
            case "ALERTA" -> "Alerta";
            default -> "Sugerencia";
        };
    }
}
