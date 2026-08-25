package com.renaser.os.calendar.application.ports.out.confirmacion;

import com.renaser.os.calendar.domain.model.confirmacion.Confirmacion;

public interface SaveConfirmacionPort {

    void upsert(Confirmacion confirmacion);
}
