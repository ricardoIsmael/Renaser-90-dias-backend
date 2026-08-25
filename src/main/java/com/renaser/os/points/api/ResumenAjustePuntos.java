package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

/** DTO liviano de vuelta hacia el módulo llamador — nunca el AjustePuntos completo. */
public record ResumenAjustePuntos(UserId participanteId, int deltaAplicado, int saldoPosterior) {
}
