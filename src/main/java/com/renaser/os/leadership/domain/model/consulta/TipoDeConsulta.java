package com.renaser.os.leadership.domain.model.consulta;

/** Qué se abrió: la ficha de cumplimiento de una semana (spec 005, 015, 017) o el diagnóstico de la persona (spec 026). */
public enum TipoDeConsulta {

    /** La ficha de una semana: lleva el lunes de la ventana que se miró. Es la que arma el resumen de las 20:00 al mentor. */
    SEMANA,

    /** El diagnóstico de la persona (026): no es de una semana, así que no lleva fecha. Queda registrado y no avisa a nadie. */
    DIAGNOSTICO
}
