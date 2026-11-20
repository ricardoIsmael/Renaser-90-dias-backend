package com.renaser.os.mentoring.application.ports.in;

/** El barrido de las 19:00: avisa al mentor de los aprendices de su grupo que hoy están en rojo (spec 057, FR-031). */
public interface AvisarRojosDelDiaUseCase {

    /**
     * Recorre a los mentores con grupo vigente a quienes ahora les toca (19:00 a 19:29 de SU zona) y publica un evento por cada aprendiz en
     * rojo medido hoy, en el día local DEL APRENDIZ.
     *
     * @return cuántos eventos publicó. Los que la bandeja ya tenía esa semana se cuentan igual: la deduplicación ocurre río abajo.
     */
    int avisar();
}
