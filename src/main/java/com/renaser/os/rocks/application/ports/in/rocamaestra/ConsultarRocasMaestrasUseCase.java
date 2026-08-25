package com.renaser.os.rocks.application.ports.in.rocamaestra;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ConsultarRocasMaestrasUseCase {

    /** Las (0-3) Rocas Maestras del propio actor. Las crea `onboarding` — ver RK-1. */
    List<RocaMaestra> misRocasMaestras(UserId actorId);
}
