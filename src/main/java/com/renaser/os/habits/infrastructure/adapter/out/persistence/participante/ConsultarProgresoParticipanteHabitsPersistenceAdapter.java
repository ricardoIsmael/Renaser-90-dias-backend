package com.renaser.os.habits.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Delega en el contrato publico de `users` (D-41). Traduce la proyeccion publica al
 * vocabulario local de `habits`, que expresa la zona horaria como {@code String}.
 *
 * <p><b>Semantica preservada:</b> la query anterior era INNER JOIN — sin fila de
 * participante, vacio. El filtro por {@code inscrito} lo mantiene igual.
 */
@Component
class ConsultarProgresoParticipanteHabitsPersistenceAdapter implements ConsultarProgresoParticipanteHabitsPort {

    private final ParticipacionProgramaFinder participacionFinder;

    ConsultarProgresoParticipanteHabitsPersistenceAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public Optional<ProgresoParticipanteHabits> deParticipante(UserId participanteId) {
        return participacionFinder.deParticipante(participanteId)
                .filter(ParticipacionPrograma::inscrito)
                .map(ConsultarProgresoParticipanteHabitsPersistenceAdapter::aProgreso);
    }

    @Override
    public List<UserId> participantesInscritosActivos() {
        return participacionFinder.participantesInscritosActivos();
    }

    private static ProgresoParticipanteHabits aProgreso(ParticipacionPrograma participacion) {
        return new ProgresoParticipanteHabits(participacion.diaPrograma(), participacion.zona().getId(),
                mapearRol(participacion.rol()), participacion.suspendido());
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
