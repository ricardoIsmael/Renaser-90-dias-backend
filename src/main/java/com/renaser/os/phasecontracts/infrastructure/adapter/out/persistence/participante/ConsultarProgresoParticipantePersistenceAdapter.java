package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.phasecontracts.application.ports.out.contrato.ConsultarProgresoParticipantePort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Delega en el contrato publico de `users` en vez de consultar
 * `participantes_programa` con una query propia (D-41: ningun modulo lee la tabla de
 * otro de frente). Este adaptador conserva la unica responsabilidad que le corresponde:
 * traducir la proyeccion publica al vocabulario local del modulo.
 *
 * <p><b>Semantica preservada:</b> antes la query hacia INNER JOIN, asi que un usuario
 * SIN fila de participante devolvia vacio y el servicio respondia 404. El contrato
 * publico devuelve la fila igual con {@code inscrito=false}, asi que el filtro se hace
 * aca — el comportamiento del modulo no cambia.
 */
@Component
class ConsultarProgresoParticipantePersistenceAdapter implements ConsultarProgresoParticipantePort {

    private final ParticipacionProgramaFinder participacionFinder;

    ConsultarProgresoParticipantePersistenceAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public Optional<ProgresoParticipante> deParticipante(UserId participanteId) {
        return participacionFinder.deParticipante(participanteId)
                .filter(ParticipacionPrograma::inscrito)
                .map(ConsultarProgresoParticipantePersistenceAdapter::aProgreso);
    }

    private static ProgresoParticipante aProgreso(ParticipacionPrograma participacion) {
        return new ProgresoParticipante(participacion.diaPrograma(), mapearRol(participacion.rol()),
                participacion.suspendido());
    }

    private static RolParticipante mapearRol(UserRole rol) {
        return switch (rol) {
            case ALCHEMIST -> RolParticipante.ALCHEMIST;
            case ADMIN -> RolParticipante.ADMIN;
            case MENTOR_LEAD -> RolParticipante.MENTOR_LEAD;
            case MENTOR -> RolParticipante.MENTOR;
            case TRAINEE -> RolParticipante.TRAINEE;
        };
    }
}
