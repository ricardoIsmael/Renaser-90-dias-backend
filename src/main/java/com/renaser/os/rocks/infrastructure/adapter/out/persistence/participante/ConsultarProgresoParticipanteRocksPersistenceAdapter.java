package com.renaser.os.rocks.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Delega en el contrato publico de `users` (D-41). `rocks` necesita ademas
 * {@code fechaInicio} y la zona real del participante para calcular sus ventanas de
 * planificacion — ambas viajan en la proyeccion publica.
 *
 * <p><b>Semantica preservada:</b> la query anterior era INNER JOIN — sin fila de
 * participante, vacio.
 */
@Component
class ConsultarProgresoParticipanteRocksPersistenceAdapter implements ConsultarProgresoParticipanteRocksPort {

    private final ParticipacionProgramaFinder participacionFinder;

    ConsultarProgresoParticipanteRocksPersistenceAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public Optional<ProgresoParticipanteRocks> deParticipante(UserId participanteId) {
        return participacionFinder.deParticipante(participanteId)
                .filter(ParticipacionPrograma::inscrito)
                .map(ConsultarProgresoParticipanteRocksPersistenceAdapter::aProgreso);
    }

    private static ProgresoParticipanteRocks aProgreso(ParticipacionPrograma participacion) {
        return new ProgresoParticipanteRocks(participacion.diaPrograma(), participacion.fechaInicio(),
                participacion.zona(), mapearRol(participacion.rol()), participacion.suspendido());
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
