package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * La semana de un mentor para su líder (FR-036): encabezado, titular de su grupo, conteo por color, aprendices ordenados por riesgo
 * con su tendencia, sus pendientes, las alertas (aprendices con 3 o más días sin actividad), lo que el líder le dijo y el foco de la
 * semana; y, si el mentor sigue su programa, su propia semana como la de un aprendiz.
 *
 * <p>Nunca trae la nota privada del mentor sobre un aprendiz (FR-037): esa nota no se lee para armar estos datos.
 *
 * @param aprendices ordenados por riesgo: rojo, ámbar, sin datos y verde; dentro de cada color, el de menor porcentaje primero
 * @param alertas    aprendices con 3 o más días medidos sin ningún cumplimiento esa semana
 * @param suSemana   su propia semana; null si no sigue su programa
 */
public record DatosDelMentor(Encabezado encabezado, TitularDelGrupo titular, ConteoPorColor conteo,
                             List<AprendizDelMentor> aprendices, Pendientes pendientes, List<AprendizEnAlerta> alertas,
                             List<LoQueLeDijoElLider> observaciones, Foco foco,
                             DatosDelAprendiz suSemana) implements DatosDelDocumento {

    /** FR-036: desde cuántos días sin actividad un aprendiz entra a las alertas. */
    public static final int DIAS_SIN_ACTIVIDAD_PARA_ALERTA = 3;

    public DatosDelMentor {
        Objects.requireNonNull(encabezado, "encabezado es obligatorio");
        Objects.requireNonNull(titular, "titular es obligatorio");
        Objects.requireNonNull(conteo, "conteo es obligatorio");
        Objects.requireNonNull(pendientes, "pendientes es obligatorio");
        aprendices = aprendices == null ? List.of() : List.copyOf(aprendices);
        alertas = alertas == null ? List.of() : List.copyOf(alertas);
        observaciones = observaciones == null ? List.of() : List.copyOf(observaciones);
    }

    @Override
    public TipoDeDocumento tipo() {
        return TipoDeDocumento.MENTOR;
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

    /** @param formacion el nombre de su formación; null si no pertenece a una sola */
    public record Encabezado(String nombre, String formacion, int grupos, LocalDate desde, LocalDate hasta, String zona) {
    }

    /**
     * @param estado   el de sus aprendices juntos, el del promedio; {@code SIN_DATOS} si ninguno tuvo datos o no lidera ningún grupo
     * @param promedio el promedio de los aprendices con datos, 1 decimal; null si ninguno tuvo
     * @param grupos   los nombres de sus grupos, para decir de cuáles habla el documento
     */
    public record TitularDelGrupo(ColorDeLaFila estado, BigDecimal promedio, int aprendices, List<String> grupos) {

        public TitularDelGrupo {
            grupos = grupos == null ? List.of() : List.copyOf(grupos);
        }
    }

    public record ConteoPorColor(int rojo, int ambar, int verde, int sinDatos) {
    }

    /**
     * @param tendencia SUBE, BAJA o IGUAL contra la semana anterior; SIN_COMPARACION si alguna de las dos no se midió
     */
    public record AprendizDelMentor(String nombre, String grupo, ColorDeLaFila estado, BigDecimal porcentaje, String tendencia) {
    }

    /**
     * @param consultas           las consultas de sus aprendices que no respondió; null si esa fuente no respondió
     * @param diasDeLaMasVieja    los días de la más vieja; null sin consultas
     * @param evaluacionEstado    CALCULADA, SIN_MUESTRA o SIN_HISTORIAL; null si la evaluación no se pudo leer
     * @param evaluacionPorcentaje el de su evaluación del mes; null si no está calculada
     * @param enRojoPorEscribir   los aprendices en rojo a los que escribirles
     */
    public record Pendientes(Integer consultas, Integer diasDeLaMasVieja, String evaluacionEstado,
                             BigDecimal evaluacionPorcentaje, List<String> enRojoPorEscribir) {

        public Pendientes {
            enRojoPorEscribir = enRojoPorEscribir == null ? List.of() : List.copyOf(enRojoPorEscribir);
        }
    }

    public record AprendizEnAlerta(String nombre, int diasSinActividad) {
    }

    /** @param tipo RECONOCIMIENTO, SUGERENCIA o ALERTA; la hora es la de la observación, para dibujarla en la zona del documento */
    public record LoQueLeDijoElLider(String tipo, Instant escritaEn, String autor, String texto) {
    }

    /** @param tipo ESCRIBIR_A_LOS_ROJOS, RESPONDER_CONSULTAS, REVISAR_ALERTAS o MANTENER_EL_RITMO; {@code cantidad} es de lo que habla */
    public record Foco(String tipo, int cantidad) {
    }
}
