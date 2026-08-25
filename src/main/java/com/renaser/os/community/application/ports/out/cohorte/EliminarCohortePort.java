package com.renaser.os.community.application.ports.out.cohorte;

import com.renaser.os.community.domain.model.cohorte.CohorteId;

public interface EliminarCohortePort {

    void eliminar(CohorteId id);
}
