package com.renaser.os.leadership.domain.model.consulta;

/** Qué se abrió: la ficha de cumplimiento de una semana (spec 005, 015, 017), el diagnóstico (spec 026) o la ruta de una carrera (spec 042). */
public enum TipoDeConsulta {

    /** La ficha de una semana: lleva el lunes de la ventana que se miró. Es la que arma el resumen de las 20:00 al mentor. */
    SEMANA,

    /** El diagnóstico de la persona (026): no es de una semana, así que no lleva fecha. Queda registrado y no avisa a nadie. */
    DIAGNOSTICO,

    /** La ruta de una carrera con GPS de la persona (042, FR-041): como el diagnóstico, sin fecha y sin aviso. */
    RUTA_DE_CARRERA
}
