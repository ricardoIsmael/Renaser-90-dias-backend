package com.renaser.os.calendar.infrastructure.adapter.out.persistence.audiencia;

import com.renaser.os.calendar.application.ports.out.audiencia.PadronDeConvocatoriaPort;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoConAprendices;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BusquedaDeUsuariosFinder;
import com.renaser.os.users.api.GruposDelPersonalFinder;
import com.renaser.os.users.api.IntegrantesDeFormacionFinder;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A quién se puede convocar (spec 044), componiendo los contratos públicos de {@code users} (personas, roles, formaciones, búsqueda) y de
 * {@code community} (grupos y quién está en cada uno hoy). No lee tablas ajenas (D-41).
 */
@Component
class PadronDeConvocatoriaAdapter implements PadronDeConvocatoriaPort {

    private static final Set<UserRole> DE_LA_FORMACION = EnumSet.of(UserRole.TRAINEE, UserRole.MENTOR, UserRole.TRAINEE_GUIDE);
    private static final Set<UserRole> PERSONAL = EnumSet.of(UserRole.ADMIN, UserRole.ALCHEMIST);

    private final GruposDelPersonalFinder gruposDelPersonal;
    private final AcompanamientoFinder acompanamiento;
    private final IntegrantesDeFormacionFinder integrantes;
    private final ParticipacionProgramaFinder participaciones;
    private final BusquedaDeUsuariosFinder busqueda;
    private final UserSummaryFinder usuarios;
    private final Clock clock;

    PadronDeConvocatoriaAdapter(GruposDelPersonalFinder gruposDelPersonal, AcompanamientoFinder acompanamiento,
                                IntegrantesDeFormacionFinder integrantes, ParticipacionProgramaFinder participaciones,
                                BusquedaDeUsuariosFinder busqueda, UserSummaryFinder usuarios, Clock clock) {
        this.gruposDelPersonal = gruposDelPersonal;
        this.acompanamiento = acompanamiento;
        this.integrantes = integrantes;
        this.participaciones = participaciones;
        this.busqueda = busqueda;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Override
    public Set<UserId> alumnosDe(UserId mentor) {
        Instant ahora = clock.now();
        Set<UserId> alumnos = new LinkedHashSet<>();
        for (var grupo : gruposDelPersonal.queLidera(mentor)) {
            alumnos.addAll(acompanamiento.aprendicesVigentes(grupo.id(), ahora));
        }
        alumnos.remove(mentor);
        return alumnos;
    }

    @Override
    public boolean esAlumnoDe(UserId persona, UserId mentor) {
        Instant ahora = clock.now();
        return !persona.equals(mentor) && gruposDelPersonal.queLidera(mentor).stream()
                .anyMatch(grupo -> acompanamiento.aprendicesVigentes(grupo.id(), ahora).contains(persona));
    }

    @Override
    public Set<UserId> personasDeLaFormacion(Formacion formacion) {
        return activosConRol(integrantes.deLaFormacion(formacion), DE_LA_FORMACION);
    }

    @Override
    public Set<UserId> aprendicesDeLaFormacion(Formacion formacion) {
        return activosConRol(integrantes.deLaFormacion(formacion), EnumSet.of(UserRole.TRAINEE));
    }

    @Override
    public Set<UserId> mentoresDeLaFormacion(Formacion formacion) {
        return activosConRol(integrantes.deLaFormacion(formacion), UserRole.ROLES_QUE_ACTUAN_COMO_MENTOR);
    }

    @Override
    public Set<UserId> personalActivo() {
        return new LinkedHashSet<>(participaciones.usuariosActivosConRol(PERSONAL));
    }

    @Override
    public List<GrupoConvocable> gruposDe(Formacion formacion) {
        return acompanamiento.gruposOperativos(clock.now()).stream()
                .filter(g -> !g.recepcion() && g.formacion() == formacion)
                .map(g -> new GrupoConvocable(g.grupoId(), g.nombre(), g.formacion(), Set.copyOf(g.aprendices())))
                .toList();
    }

    @Override
    public Set<UserId> buscar(String texto) {
        return busqueda.coincidenCon(texto);
    }

    @Override
    public Map<UserId, Convocado> fichas(Collection<UserId> personas) {
        if (personas.isEmpty()) {
            return Map.of();
        }
        Map<UserId, String> grupoDe = new HashMap<>();
        for (GrupoConAprendices grupo : acompanamiento.gruposOperativos(clock.now())) {
            grupo.aprendices().forEach(a -> grupoDe.putIfAbsent(a, grupo.nombre()));
            if (grupo.mentorId() != null) {
                grupoDe.putIfAbsent(grupo.mentorId(), grupo.nombre());
            }
        }
        Map<UserId, Convocado> resultado = new HashMap<>();
        usuarios.findByIds(personas).forEach((id, u) -> resultado.put(id,
                new Convocado(id, u.fullName(), u.avatarUrl(), u.role().nombreVisible(), grupoDe.get(id))));
        return resultado;
    }

    @Override
    public Optional<RolUsuario> rolActivo(UserId persona) {
        return usuarios.findById(persona)
                .filter(u -> u.status() == UserStatus.ACTIVE)
                .map(u -> aRolUsuario(u.role()));
    }

    private Set<UserId> activosConRol(Collection<UserId> candidatos, Set<UserRole> roles) {
        if (candidatos.isEmpty()) {
            return Set.of();
        }
        return usuarios.findByIds(candidatos).values().stream()
                .filter(u -> u.status() == UserStatus.ACTIVE && roles.contains(u.role()))
                .map(UserSummary::id)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static RolUsuario aRolUsuario(UserRole rol) {
        return switch (rol) {
            case ALCHEMIST -> RolUsuario.ALCHEMIST;
            case ADMIN, IT_SUPPORT -> RolUsuario.ADMIN;
            case MENTOR_LEAD -> RolUsuario.MENTOR_LEAD;
            case MENTOR, TRAINEE_GUIDE -> RolUsuario.MENTOR;
            case TRAINEE -> RolUsuario.TRAINEE;
        };
    }
}
