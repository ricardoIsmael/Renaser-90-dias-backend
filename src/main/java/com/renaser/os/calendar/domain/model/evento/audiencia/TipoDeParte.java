package com.renaser.os.calendar.domain.model.evento.audiencia;

/**
 * Lo que se puede elegir en «¿Para quién es?» (spec 044, FR-002). Cada parte es una manera de nombrar personas; se suman (FR-003).
 *
 * <ul>
 *   <li>{@link #PERSONA}: una persona por su nombre (lista fija).</li>
 *   <li>{@link #MIS_ALUMNOS}: los integrantes vigentes de los grupos que acompaña el mentor que crea el evento (se lee en cada momento).</li>
 *   <li>{@link #GRUPO}: un grupo (se lee en cada momento).</li>
 *   <li>{@link #MENTORES}: todos los mentores y guías de aprendiz de la formación del evento (se lee en cada momento).</li>
 *   <li>{@link #COLOR}: los aprendices de un color del semáforo, en una foto tomada al guardar (lista fija, decisión P1).</li>
 *   <li>{@link #PERSONAL}: Administración y Alquimista; con una persona, solo ella (lista fija); sin persona, todo el personal (en cada momento).</li>
 * </ul>
 */
public enum TipoDeParte {
    PERSONA,
    MIS_ALUMNOS,
    GRUPO,
    MENTORES,
    COLOR,
    PERSONAL
}
