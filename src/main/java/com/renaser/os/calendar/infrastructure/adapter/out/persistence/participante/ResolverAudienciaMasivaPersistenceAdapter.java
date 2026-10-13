package com.renaser.os.calendar.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.calendar.application.ports.out.participante.ResolverAudienciaMasivaPort;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Delega en el contrato publico de `users` (D-41). Las tres operaciones siguen siendo
 * EN LOTE — una consulta por evento, nunca una por aprendiz, igual que
 * {@code resolveAudience} del repo viejo: llamar al finder por usuario seria el N+1 que
 * este puerto existe para evitar.
 */
@Component
class ResolverAudienciaMasivaPersistenceAdapter implements ResolverAudienciaMasivaPort {

    private static final Set<UserRole> SOLO_APRENDICES = EnumSet.of(UserRole.TRAINEE);

    private final ParticipacionProgramaFinder participacionFinder;

    ResolverAudienciaMasivaPersistenceAdapter(ParticipacionProgramaFinder participacionFinder) {
        this.participacionFinder = participacionFinder;
    }

    @Override
    public List<UserId> traineesActivos() {
        return participacionFinder.usuariosActivosConRol(SOLO_APRENDICES);
    }

    @Override
    public List<UserId> activosConRoles(Set<RolUsuario> roles) {
        if (roles.isEmpty()) {
            return List.of();
        }
        return participacionFinder.usuariosActivosConRol(aUserRoles(roles));
    }

    @Override
    public List<ParticipanteConDia> traineesActivosConDiaPrograma() {
        return participacionFinder.usuariosActivosConDiaPrograma(SOLO_APRENDICES).stream()
                .map(fila -> new ParticipanteConDia(fila.id(), fila.diaPrograma()))
                .toList();
    }

    private static Set<UserRole> aUserRoles(Set<RolUsuario> roles) {
        Set<UserRole> traducidos = EnumSet.noneOf(UserRole.class);
        for (RolUsuario rol : roles) {
            switch (rol) {
                case ALCHEMIST -> traducidos.add(UserRole.ALCHEMIST);
                case ADMIN -> traducidos.add(UserRole.ADMIN);
                case MENTOR_LEAD -> traducidos.add(UserRole.MENTOR_LEAD);
                // Un evento para mentores llega también a los guías de aprendiz (D-265: hacen lo mismo).
                case MENTOR -> traducidos.addAll(UserRole.ROLES_QUE_ACTUAN_COMO_MENTOR);
                case TRAINEE -> traducidos.add(UserRole.TRAINEE);
            }
        }
        return traducidos;
    }
}
