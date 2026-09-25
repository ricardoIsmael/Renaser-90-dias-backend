package com.renaser.os.mentoring.application.ports.in;

/**
 * Resumen semanal del semáforo de cumplimiento (D-168, docs/arquitectura/SEMAFORO_DEL_APRENDIZ.md
 * §1.2): cuando un grupo cierra su semana sábado→viernes, se le avisa a su mentor, y al líder,
 * admin y alquimista con la suma de todos los grupos.
 */
public interface ResumirSemanaDelSemaforoUseCase {

    /**
     * Recorre los grupos con mentor vigente y publica el resumen de los que acaban de cerrar su
     * semana, más uno general con la suma.
     *
     * @return cuántos resúmenes de grupo se publicaron. Una corrida repetida dentro de la ventana
     *         los vuelve a publicar con la misma clave: la deduplicación la hace {@code notifications}.
     */
    int resumir();
}
