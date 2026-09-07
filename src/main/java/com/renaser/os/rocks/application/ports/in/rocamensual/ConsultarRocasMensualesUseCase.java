package com.renaser.os.rocks.application.ports.in.rocamensual;

import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ConsultarRocasMensualesUseCase {

    /**
     * Los tramos mensuales del propio actor: como maximo nueve (tres ejes x tres meses), y
     * normalmente menos, porque el aprendiz define el mes en curso y no los tres de una.
     */
    List<RocaMensual> misRocasMensuales(UserId actorId);
}
