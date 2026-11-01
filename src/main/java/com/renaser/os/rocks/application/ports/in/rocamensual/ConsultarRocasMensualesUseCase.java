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

    /**
     * Cuantos dias dura el programa de ESTA persona (90, o 365 en el Diplomado de 1 ano, spec 022). Hace falta para decir en que dia cierra
     * cada mes: el del ultimo mes es el ultimo dia de SU programa (365 y no 360).
     */
    int duracionDelPrograma(UserId actorId);
}
