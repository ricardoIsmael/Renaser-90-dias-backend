package com.renaser.os.mentoring.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * La semana de seguimiento de una persona: lo que la ficha del mentor, la de administración y la del líder
 * (spec 005) muestran día por día. Es también la respuesta JSON de {@code …/progress} y {@code …/weekly-progress}.
 *
 * <p><b>Vive en {@code api}</b> (antes, dentro del caso de uso del mentor) para que {@code leadership} la lea por
 * {@link SemanaDeSeguimientoFinder} sin armar una segunda semana: que el armado sea UNO solo es el requisito (V22).
 *
 * @param inicioDeSemana primer día de la ventana pedida, en la zona de la persona. Es el lunes en las fichas del
 *                       mentor y de administración; el sábado (o el primer día de la ventana vigente) en la del líder
 * @param finDeSemana    último día de la ventana
 * @param cobertura      de datos. Que falte información no es lo mismo que incumplir: hay tres estados, no dos
 *                       (plan.md §7)
 */
public record SemanaDelAlumno(UUID grupoId, UUID alumnoId, String nombre, LocalDate inicioDeSemana,
                              LocalDate finDeSemana, Integer diaDePrograma, String zona, List<DiaDelAlumno> dias,
                              ResumenSemana resumen, String cobertura, Instant actualizadoEn) {
}
