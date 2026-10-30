/**
 * Gestión del cuerpo de mentores por el Líder de Mentores (SDD 002; D-241): el padrón con sus
 * indicadores, la ficha de cada mentor, las observaciones del líder y el reporte del período.
 *
 * <p><b>Por qué un módulo aparte.</b> Junta datos de cuatro módulos —quiénes son los mentores
 * ({@code users}), su semáforo y su evaluación ({@code mentoring}), sus tickets ({@code support})— y
 * solo los LEE por sus {@code api/}. Nadie depende de él, así que no puede cerrar un ciclo, y no
 * comparte archivos con la lógica de acompañamiento, que sigue siendo de {@code community} y
 * {@code mentoring} (propuesta PL-09 del SDD 002).
 *
 * <p>Desde la spec 005 también lee la ficha de cada persona del grupo, y desde la spec 018 (D-291) dice si «programó» su día de
 * hoy: para eso lee {@code habits.api} (el horario resuelto) y {@code rocks.api} (las acciones de hoy). Ninguno de los dos
 * depende de este módulo, así que no hay ciclo.
 *
 * <p>Lo único que escribe es lo suyo: las observaciones del líder sobre un mentor
 * ({@code observaciones_mentor}, V89, append-only).
 *
 * <p>El líder NO entra al detalle de un grupo ni ve aprendices con nombre (decisión del dueño,
 * 2026-10-01): todo lo que sale de acá sobre aprendices son cantidades.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Leadership")
package com.renaser.os.leadership;
