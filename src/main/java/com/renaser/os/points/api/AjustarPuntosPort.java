package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

public interface AjustarPuntosPort {

    ResumenAjustePuntos ajustar(UserId participanteId, MotivoPuntos motivo, int delta, String nota);
}
