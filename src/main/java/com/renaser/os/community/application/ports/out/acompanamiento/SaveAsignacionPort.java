package com.renaser.os.community.application.ports.out.acompanamiento;

import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;

public interface SaveAsignacionPort {

    AsignacionCelula save(AsignacionCelula asignacion);
}
