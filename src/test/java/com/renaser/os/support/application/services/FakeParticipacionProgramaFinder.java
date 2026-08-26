package com.renaser.os.support.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Solo importa la relacion aprendiz -> mentor asignado, que es lo que
 * {@code TicketMentorService.requireMentorAsignado} necesita verificar (E-38). El resto
 * de los metodos del contrato no los usa este servicio.
 */
class FakeParticipacionProgramaFinder implements ParticipacionProgramaFinder {

    private final Map<UserId, UserId> mentorPorAprendiz = new HashMap<>();

    FakeParticipacionProgramaFinder conMentorAsignado(UserId aprendiz, UserId mentor) {
        mentorPorAprendiz.put(aprendiz, mentor);
        return this;
    }

    @Override
    public Optional<ParticipacionPrograma> deParticipante(UserId participanteId) {
        if (!mentorPorAprendiz.containsKey(participanteId)) {
            return Optional.empty();
        }
        return Optional.of(new ParticipacionPrograma(participanteId, true, 10, LocalDate.of(2026, 8, 1),
                ZoneId.of("America/Lima"), FasePrograma.PHASE_1_REBIRTH, null,
                mentorPorAprendiz.get(participanteId), UserRole.TRAINEE, false));
    }

    @Override
    public List<UserId> miembrosActivosDeCelula(UUID celulaId) {
        return List.of();
    }

    @Override
    public List<UserId> miembrosDeCelula(UUID celulaId) {
        return List.of();
    }

    @Override
    public List<UserId> usuariosActivosConRol(Set<UserRole> roles) {
        return List.of();
    }

    @Override
    public List<UsuarioConDiaPrograma> usuariosActivosConDiaPrograma(Set<UserRole> roles) {
        return List.of();
    }

    @Override
    public List<UserId> participantesInscritosActivos() {
        return List.of();
    }

    @Override
    public int contarMiembrosDeCelula(UUID celulaId) {
        return 0;
    }
}
