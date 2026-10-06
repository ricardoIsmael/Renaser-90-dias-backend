package com.renaser.os.habits.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;

/**
 * La zona horaria de muchos participantes a la vez, para el barrido de expiracion (E-534): el dia de cada uno termina
 * a SU medianoche. En lote para no hacer una consulta por persona en cada corrida horaria.
 */
public interface ConsultarZonasDeParticipantesPort {

    /**
     * @return la zona de cada participante con programa activado, en UNA consulta. Sin clave: no activo su
     * programa (quien consume pregunta por esa persona sola).
     */
    Map<UserId, ZoneId> deProgramasActivados(Collection<UserId> participantes);
}
