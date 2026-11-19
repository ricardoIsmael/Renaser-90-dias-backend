package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * La semana de una persona de sábado a viernes, con las secciones del documento semanal del aprendiz en el orden de FR-035: encabezado,
 * titular, cuatro cifras contra la semana anterior, tabla pilar por día, evidencias, «En sus palabras», la evaluación del mes de su
 * mentor, el foco de la próxima semana y el detalle día por día. También es la semana propia de un mentor que sigue su programa
 * (FR-036): ahí el semáforo no lo mide ({@link ColorDeLaFila#NO_SE_MIDE}) y no tiene mentor.
 *
 * <p>Una semana sin ningún día con algo programado ({@link #sinActividad()}) se dice en una línea y no trae tablas vacías (FR-038).
 *
 * @param palabras           lo que la persona escribió al cumplir sus hábitos esa semana; vacía si no escribió nada: la sección no se inventa
 * @param evaluacionDelMentor la de su mentor en el mes; null si no tiene mentor o si la evaluación no se pudo leer
 * @param foco               null si la semana no tuvo nada programado
 */
public record DatosDelAprendiz(Encabezado encabezado, Titular titular, Cifras cifras, TablaDePilares tabla,
                               List<EvidenciaDeLaSemana> evidencias, List<PalabraDeLaPersona> palabras,
                               EvaluacionDelMentor evaluacionDelMentor, FocoDeLaSemana foco,
                               List<DetalleDelDia> detalle) implements DatosDelDocumento {

    public DatosDelAprendiz {
        Objects.requireNonNull(encabezado, "encabezado es obligatorio");
        Objects.requireNonNull(titular, "titular es obligatorio");
        Objects.requireNonNull(cifras, "cifras es obligatorio");
        Objects.requireNonNull(tabla, "tabla es obligatoria");
        evidencias = evidencias == null ? List.of() : List.copyOf(evidencias);
        palabras = palabras == null ? List.of() : List.copyOf(palabras);
        detalle = detalle == null ? List.of() : List.copyOf(detalle);
    }

    @Override
    public TipoDeDocumento tipo() {
        return TipoDeDocumento.APRENDIZ;
    }

    @Override
    public LocalDate desde() {
        return encabezado.desde();
    }

    @Override
    public LocalDate hasta() {
        return encabezado.hasta();
    }

    @Override
    public String zona() {
        return encabezado.zona();
    }

    /** FR-038: ningún día de la semana tuvo hábitos programados. */
    public boolean sinActividad() {
        return cifras.diasProgramados().valor() == 0;
    }

    /**
     * @param formacion    el nombre de la formación de la persona: el plazo sale de ella, nunca de un texto fijo
     * @param dia          su día del programa al cerrar la semana (o hoy, si la semana corre); null si todavía no hay
     * @param duracionDias la duración de SU programa en días
     * @param mentor       null si no tiene mentor hoy
     * @param grupo        null si no está en un grupo
     */
    public record Encabezado(String nombre, String formacion, Integer dia, int duracionDias, String mentor, String grupo,
                             LocalDate desde, LocalDate hasta, String zona) {
    }

    /** @param porcentaje el de la semana, 1 decimal; null si ningún día se midió (nunca cero por falta de datos) */
    public record Titular(ColorDeLaFila estado, BigDecimal porcentaje) {
    }

    /** Las cuatro cifras de la semana, cada una contra la semana anterior. */
    public record Cifras(Cifra habitosCumplidos, Cifra diasActivos, Cifra evidencias, Cifra diasProgramados) {
    }

    /** @param anterior lo mismo en la semana anterior; null si esa semana es anterior a su Día 1 y no hay con qué comparar */
    public record Cifra(int valor, Integer anterior) {

        /** Cuánto subió o bajó; null si no hay semana anterior. */
        public Integer diferencia() {
            return anterior == null ? null : valor - anterior;
        }
    }

    /** @param dias los siete días de la semana, de sábado a viernes */
    public record TablaDePilares(List<LocalDate> dias, List<FilaDePilar> filas) {

        public TablaDePilares {
            dias = dias == null ? List.of() : List.copyOf(dias);
            filas = filas == null ? List.of() : List.copyOf(filas);
        }
    }

    /** @param celdas una por día de la tabla, en el mismo orden */
    public record FilaDePilar(String pilar, List<CeldaDelDia> celdas) {

        public FilaDePilar {
            celdas = celdas == null ? List.of() : List.copyOf(celdas);
        }
    }

    public record CeldaDelDia(int cumplidos, int programados) {
    }

    /**
     * @param tipo   FOTO, VIDEO, AUDIO, TEXTO o CAPTURA
     * @param estado la revisión de la evidencia: VALIDA, RECHAZADA, REVISION_MANUAL, PENDIENTE o ANULADA_ADMIN
     */
    public record EvidenciaDeLaSemana(String tipo, LocalDate fecha, String estado, String habito) {
    }

    public record PalabraDeLaPersona(LocalDate fecha, String habito, String texto) {
    }

    /** @param estado CALCULADA, SIN_MUESTRA o SIN_HISTORIAL; el porcentaje solo existe si es CALCULADA */
    public record EvaluacionDelMentor(String mes, BigDecimal porcentaje, String estado) {
    }

    /**
     * El pilar en que la semana fue más floja, para dónde poner el foco la semana que viene; null si todos los pilares fueron completos
     * y se trata de sostener el ritmo.
     */
    public record FocoDeLaSemana(String pilar, int cumplidos, int programados) {
    }

    /** @param motivo por qué el día no tiene nada que medir, en la clave de {@code MotivoDelDia}; null si se midió */
    public record DetalleDelDia(LocalDate fecha, Integer dia, String motivo, List<HabitoDelDia> habitos) {

        public DetalleDelDia {
            habitos = habitos == null ? List.of() : List.copyOf(habitos);
        }
    }

    /** @param entrega NO_REQUERIDA, SIN_ENTREGA o ENTREGADA; {@code estado} es el del hábito (COMPLETADO, FALLIDO, EXPIRADO, EN_CURSO, PENDIENTE) */
    public record HabitoDelDia(String pilar, String titulo, String estado, String entrega) {
    }
}
