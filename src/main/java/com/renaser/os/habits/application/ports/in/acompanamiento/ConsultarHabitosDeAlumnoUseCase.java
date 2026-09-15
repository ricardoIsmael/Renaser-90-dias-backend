package com.renaser.os.habits.application.ports.in.acompanamiento;

import com.renaser.os.habits.application.ports.in.habitosaprendiz.ConsultarHabitosDeAprendizUseCase.VistaHabitosDeAprendiz;

/**
 * Los habitos de un alumno, leidos por quien lo acompaña (2026-09-15). Hasta hoy esta vista existia
 * solo detras del guard de ADMIN/ALCHEMIST ({@code GET /api/v1/admin/trainees/{id}/habits}): un
 * mentor veia el CUMPLIMIENTO dia a dia de su aprendiz, pero no su configuracion —que habitos
 * tiene, a que hora, cuales renombro, que tiene pendiente de cambio—, que es lo primero que se
 * mira antes de escribirle.
 *
 * <p>Devuelve exactamente la misma vista que el panel de admin, a proposito: dos formas distintas
 * del mismo dato se desincronizan, y ya hay un caso escrito en este repo (D-125, la columna
 * generada `fecha_graduacion_esperada`). Lo que cambia es la PUERTA y el guard, no el contenido.
 */
public interface ConsultarHabitosDeAlumnoUseCase {

    VistaHabitosDeAprendiz habitosDeAlumno(ConsultaDeAcompanante consulta);
}
