package com.renaser.os.habits.api;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Qué pasó UN día con los hábitos del catálogo de VARIAS personas (spec 029: «el líder ve todos los hábitos de todos los alumnos»). Sale de
 * {@code registros_habito}, la misma fuente de la ficha de cada persona: no se guarda ninguna copia, así las cifras de la lista y las de la ficha
 * no pueden discrepar.
 *
 * <p><b>En lote, siempre:</b> una consulta para todo el padrón, apoyada en {@code registros_dia_idx}; nunca persona por persona (FR-017).
 * Solo hábitos del catálogo (los propios de cada persona no son comparables entre ellas) y solo de las formaciones del {@code alcance}
 * (spec 023): un registro de un hábito de otra formación —el que quedó de antes de un traslado— nunca sale.
 *
 * <p><b>No autoriza</b>, igual que los otros finders de este paquete: quien llama ya estableció que el actor puede ver a esas personas.
 */
public interface CumplimientoDelDiaFinder {

    /**
     * @param fecha el día local de las personas ({@code fecha_ejecucion}); cada una tiene su propio cierre del día en su zona
     * @return los registros de ese día; vacío si no hay ninguno, nunca null
     */
    List<RegistroDelDia> deVariasPersonas(Collection<UserId> personas, LocalDate fecha, AlcanceDeFormacion alcance);

    /**
     * @param titulo       el del catálogo, nunca el nombre propio que alguien le haya puesto a una bebida
     * @param categoriaClave  la categoría del hábito en su formación (la clave de la base)
     * @param categoriaCodigo cómo la dice la API de hábitos: el inglés de las cuatro de la Escuela (`BODY`…) o la clave
     * @param formacion    el código de la formación del hábito
     * @param opcional     ese día no era exigible: sin cumplirse, no cuenta como falta
     * @param completadoEn el instante en que se cumplió; {@code null} si no se cumplió
     */
    record RegistroDelDia(UUID registroId, UserId personaId, UUID habitoId, String titulo, String categoriaClave,
                          String categoriaCodigo, String formacion, EstadoObligacion estado, boolean opcional, Instant completadoEn) {
    }
}
