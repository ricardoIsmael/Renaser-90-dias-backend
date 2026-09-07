package com.renaser.os.rocks.application.ports.out.rocamensual;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Optional;

/**
 * Lectura de los tramos mensuales del plan.
 *
 * <p>{@link #deParticipante} pide por persona aunque la tabla cuelgue de la Roca Maestra: es la
 * pregunta que de verdad hace la pantalla ("mostrame mis objetivos mensuales"), y el puerto se
 * nombra por intencion de negocio, no por como esta guardado (CLAUDE.md §5.4.8). Que para
 * responderla haya que pasar por {@code rocas_maestras} es problema del adaptador — las dos
 * tablas son de este mismo modulo.
 */
public interface LoadRocaMensualPort {

    /** Los (0-9) tramos mensuales del participante: hasta tres meses por cada uno de los tres ejes. */
    List<RocaMensual> deParticipante(UserId participanteId);

    Optional<RocaMensual> deMaestraYMes(RocaMaestraId rocaMaestraId, int numeroMes);
}
