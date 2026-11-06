package com.renaser.os.calendar.application.services;

import com.renaser.os.calendar.application.ports.out.audiencia.ColoresDelSemaforoPort;
import com.renaser.os.calendar.application.ports.out.audiencia.PadronDeConvocatoriaPort;
import com.renaser.os.calendar.application.ports.out.celula.ConsultarMiembrosCelulaPort;
import com.renaser.os.calendar.application.ports.out.celula.ConsultarPertenenciaAGrupoPort;
import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionPermisivo;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Un programa chico en memoria para las pruebas de la spec 044: un mentor con su grupo de cuatro aprendices (dos en rojo, uno en amarillo, uno
 * en verde), otro mentor con su grupo, un aprendiz suelto, un líder, una administradora y una alquimista. Cuenta cuántas veces se lee el semáforo.
 */
final class MundoDeAudiencia {

    static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");

    final UserId mentor = id("mentor");
    final UserId otroMentor = id("otro-mentor");
    final UserId lider = id("lider");
    final UserId admin = id("admin");
    final UserId alquimista = id("alquimista");
    final UserId rojo1 = id("rojo1");
    final UserId rojo2 = id("rojo2");
    final UserId amarillo = id("amarillo");
    final UserId verde = id("verde");
    final UserId deOtroGrupo = id("de-otro-grupo");
    final UserId sinGrupo = id("sin-grupo");
    final UUID grupo = UUID.randomUUID();
    final UUID otroGrupo = UUID.randomUUID();

    final Map<UserId, ColorDeAudiencia> semaforo = new HashMap<>();
    final Map<UserId, RolUsuario> roles = new HashMap<>();
    final Map<UUID, Set<UserId>> integrantes = new HashMap<>();
    final Map<UUID, UserId> mentorDe = new HashMap<>();
    int lecturasDelSemaforo;

    final PadronDeConvocatoriaPort padron = new Padron();
    final ColoresDelSemaforoPort colores = personas -> {
        lecturasDelSemaforo++;
        Map<UserId, ColorDeAudiencia> r = new HashMap<>();
        personas.forEach(p -> Optional.ofNullable(semaforo.get(p)).ifPresent(c -> r.put(p, c)));
        return r;
    };
    final ConsultarMiembrosCelulaPort celulas = grupoId -> {
        List<UserId> todos = new ArrayList<>(integrantes.getOrDefault(grupoId, Set.of()));
        Optional.ofNullable(mentorDe.get(grupoId)).ifPresent(todos::add);
        return todos;
    };
    final ConsultarPertenenciaAGrupoPort pertenencia = (grupoId, persona) -> integrantes.getOrDefault(grupoId, Set.of()).contains(persona)
            || persona.equals(mentorDe.get(grupoId));
    final AudienciaElegidaService servicio;

    MundoDeAudiencia() {
        integrantes.put(grupo, new LinkedHashSet<>(List.of(rojo1, rojo2, amarillo, verde)));
        integrantes.put(otroGrupo, new LinkedHashSet<>(List.of(deOtroGrupo)));
        mentorDe.put(grupo, mentor);
        mentorDe.put(otroGrupo, otroMentor);
        semaforo.put(rojo1, ColorDeAudiencia.ROJO);
        semaforo.put(rojo2, ColorDeAudiencia.ROJO);
        semaforo.put(amarillo, ColorDeAudiencia.AMARILLO);
        semaforo.put(verde, ColorDeAudiencia.VERDE);
        semaforo.put(deOtroGrupo, ColorDeAudiencia.ROJO);
        for (UserId a : List.of(rojo1, rojo2, amarillo, verde, deOtroGrupo, sinGrupo)) {
            roles.put(a, RolUsuario.TRAINEE);
        }
        roles.put(mentor, RolUsuario.MENTOR);
        roles.put(otroMentor, RolUsuario.MENTOR);
        roles.put(lider, RolUsuario.MENTOR_LEAD);
        roles.put(admin, RolUsuario.ADMIN);
        roles.put(alquimista, RolUsuario.ALCHEMIST);
        servicio = new AudienciaElegidaService(padron, colores, celulas, pertenencia, new AlcanceDeFormacionPermisivo(), FixedClock.at(AHORA));
    }

    private static UserId id(String nombre) {
        return UserId.of(UUID.nameUUIDFromBytes(nombre.getBytes()));
    }

    Set<UserId> aprendices() {
        Set<UserId> r = new LinkedHashSet<>();
        roles.forEach((p, rol) -> {
            if (rol == RolUsuario.TRAINEE) {
                r.add(p);
            }
        });
        return r;
    }

    private final class Padron implements PadronDeConvocatoriaPort {

        @Override
        public Set<UserId> alumnosDe(UserId m) {
            Set<UserId> r = new LinkedHashSet<>();
            mentorDe.forEach((g, quien) -> {
                if (quien.equals(m)) {
                    r.addAll(integrantes.get(g));
                }
            });
            return r;
        }

        @Override
        public boolean esAlumnoDe(UserId persona, UserId m) {
            return alumnosDe(m).contains(persona);
        }

        @Override
        public Set<UserId> personasDeLaFormacion(Formacion formacion) {
            Set<UserId> r = new LinkedHashSet<>(aprendices());
            r.add(mentor);
            r.add(otroMentor);
            return r;
        }

        @Override
        public Set<UserId> aprendicesDeLaFormacion(Formacion formacion) {
            return aprendices();
        }

        @Override
        public Set<UserId> mentoresDeLaFormacion(Formacion formacion) {
            return new LinkedHashSet<>(List.of(mentor, otroMentor));
        }

        @Override
        public Set<UserId> personalActivo() {
            return new LinkedHashSet<>(List.of(admin, alquimista));
        }

        @Override
        public List<GrupoConvocable> gruposDe(Formacion formacion) {
            return List.of(new GrupoConvocable(grupo, "Fénix", formacion, integrantes.get(grupo)),
                    new GrupoConvocable(otroGrupo, "Cóndor", formacion, integrantes.get(otroGrupo)));
        }

        @Override
        public Set<UserId> buscar(String texto) {
            return Set.of();
        }

        @Override
        public Map<UserId, Convocado> fichas(Collection<UserId> personas) {
            Map<UserId, Convocado> r = new HashMap<>();
            personas.forEach(p -> r.put(p, new Convocado(p, "Persona " + p.value().toString().substring(0, 4), null, "Aprendiz", null)));
            return r;
        }

        @Override
        public Optional<RolUsuario> rolActivo(UserId persona) {
            return Optional.ofNullable(roles.get(persona));
        }
    }
}
