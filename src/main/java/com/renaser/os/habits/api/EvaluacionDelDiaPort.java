package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * La marca de «día cerrado» de «Cierra tu día» (spec 017) vista desde otro módulo: una entrada de diario de tipo
 * {@code EVALUACION_DIA} por persona y fecha (existe desde {@code V1}; nadie la escribía). Es de {@code habits} porque
 * {@code entradas_diario} es suya (D-41): {@code rocks}, que orquesta el cierre, no manda SQL contra ella.
 *
 * <p><b>No valida el día.</b> Quien llama ({@code rocks.CierreDelDiaService}) ya resolvió «hoy» en la zona de la persona y exigió
 * que {@code fecha} lo sea. Aquí solo se guarda.
 *
 * <p>La nota es texto personal: quien la reciba no la loguea.
 */
public interface EvaluacionDelDiaPort {

    /**
     * Deja el día como cerrado: crea la marca o, si ya estaba, reemplaza su nota (sin nota la borra). La hora del primer cierre
     * se conserva.
     *
     * @param nota la nota del día, o {@code null}; hasta {@code RespuestaDeCierre.NOTA_MAXIMA} caracteres
     */
    MarcaDeCierre dejarCerrado(UserId participanteId, LocalDate fecha, String nota);

    /**
     * Lo mismo con el instante ya decidido (spec 048): una marca hecha sin red queda con la hora real en que se hizo, tanto su
     * {@code creado_en} (si es nueva) como su {@code actualizado_en}.
     */
    MarcaDeCierre dejarCerrado(UserId participanteId, LocalDate fecha, String nota, Instant referencia);

    /**
     * La última vez que se cerró ese día ({@code entradas_diario.actualizado_en}), o vacío si no hay marca. Es la hora lógica con la
     * que se decide un choque (spec 048, D13): si cambió después de lo que se hizo sin red, gana el servidor.
     */
    java.util.Optional<Instant> actualizadoEn(UserId participanteId, LocalDate fecha);

    /** Las marcas de la persona entre dos fechas (ambas inclusive), por fecha. Vacía si no hay; nunca null. */
    List<MarcaDeCierre> marcasEntre(UserId participanteId, LocalDate desde, LocalDate hasta);

    /**
     * @param nota     la nota del día, o {@code null}
     * @param cerradoEn el instante del PRIMER cierre de ese día
     */
    record MarcaDeCierre(LocalDate fecha, String nota, Instant cerradoEn) {
    }
}
