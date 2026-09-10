/**
 * Acompañamiento visto por el mentor: la semana de un alumno y la evaluación mensual propia.
 *
 * <p><b>Por qué es un módulo aparte y no parte de {@code community}.</b> Estas dos lecturas
 * cruzan hacia {@code habits} (qué le tocaba) y {@code evidence} (qué entregó). Ponerlas en
 * {@code community} cierra un ciclo que Spring Modulith rechaza:
 * {@code community → evidence → points → community}, porque {@code evidence} ya usa
 * {@code points.AjustarPuntosPort} y {@code points} ya usa {@code community.CelulaFinder} para el
 * ranking de células. Lo detectó {@code ArchitectureTest}, no una intuición.
 *
 * <p>Este módulo solo consume APIs públicas —{@code community.api}, {@code habits.api},
 * {@code evidence.api}, {@code points.api}, {@code users.api}— y nadie depende de él, así que no
 * introduce ciclos. No importa internals de ninguno: es la diferencia con el "módulo transversal"
 * que plan.md §2 prohíbe.
 *
 * <p>Lo que NO vive acá: células, cohortes y el historial de asignaciones, que son de
 * {@code community}. Tampoco la fórmula de cumplimiento, que tiene un solo dueño en
 * {@code points}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Mentoring")
package com.renaser.os.mentoring;
