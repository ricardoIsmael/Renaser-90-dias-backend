package com.renaser.os.mentoring.domain.model.huecos;

/**
 * De dónde sale un bloque ocupado de la agenda de un día (spec 015, corregido 2026-10-29): un hábito, una acción del
 * día, un evento o un tramo que la persona dijo estar ocupada con SER. La app dibuja los tres primeros con su nombre y
 * el cuarto solo como «Ocupado»: la agenda personal no lleva etiqueta.
 */
public enum TipoDeOcupado {
    HABITO,
    ACCION,
    EVENTO,
    AGENDA
}
