package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoConAprendices;
import com.renaser.os.leadership.application.ports.in.ConsultarPersonasDelProgramaUseCase.PaginaDePersonas;
import com.renaser.os.leadership.application.ports.in.ConsultarPersonasDelProgramaUseCase.PersonaDelPrograma;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.domain.model.cumplimiento.BusquedaPorNombre;
import com.renaser.os.leadership.domain.model.cumplimiento.GrupoDeLaPersona;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasDePruebaFinder;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Quiénes son «los aprendices del programa» para el líder, tengan grupo o no (spec 021): cualquier aprendiz con la
 * cuenta activa que está en el programa y no es una cuenta de prueba. Es el mismo criterio de «se muestra» de
 * {@link PersonasDelGrupo} (spec 005, FR-002 y SC-006), pero sin exigir que la persona esté en un grupo con mentor.
 *
 * <p>Una persona que no existe, que no es aprendiz, que está suspendida o es de prueba es 404, y la respuesta no
 * distingue cuál: el id de una cuenta que no se muestra no se confirma. Va DESPUÉS del guard de rol, como en el grupo
 * (RL-08). Todo entra por APIs públicas.
 */
@Component
class PersonasDelPrograma {

    private final AcompanamientoFinder acompanamiento;
    private final ParticipacionProgramaFinder participaciones;
    private final UserSummaryFinder usuarios;
    private final CuentasDePruebaFinder pruebas;

    PersonasDelPrograma(AcompanamientoFinder acompanamiento, ParticipacionProgramaFinder participaciones,
                        UserSummaryFinder usuarios, CuentasDePruebaFinder pruebas) {
        this.acompanamiento = acompanamiento;
        this.participaciones = participaciones;
        this.usuarios = usuarios;
        this.pruebas = pruebas;
    }

    /** La persona y el grupo con que se la mira; 404 si no es un aprendiz del programa que se muestre. */
    Mirada mirar(UserId personaId, Instant ahora) {
        UserSummary perfil = usuarios.findById(personaId).filter(this::seMuestra).orElseThrow(PersonasDelPrograma::noEncontrada);
        ParticipacionPrograma participacion = participaciones.deParticipante(personaId)
                .filter(ParticipacionPrograma::inscrito).orElseThrow(PersonasDelPrograma::noEncontrada);
        GrupoDeLaPersona grupo = GrupoDeLaPersona.elegir(gruposDe(Set.of(personaId), ahora).getOrDefault(personaId, List.of()),
                participacion.celulaId()).orElse(null);
        return new Mirada(new Miembro(perfil, participacion, false), grupo);
    }

    /**
     * Una página de los aprendices que coinciden con la búsqueda, por nombre. El padrón se lee entero (son cientos, no
     * miles) y se filtra y ordena en memoria: lo caro —el programa y el grupo de cada uno— se pide solo para la página.
     */
    PaginaDePersonas listar(String busqueda, int pagina, int tamano, Instant ahora) {
        List<UserSummary> coinciden = padron().stream()
                .filter(perfil -> BusquedaPorNombre.coincide(perfil.fullName(), busqueda))
                .sorted(Comparator.comparing(UserSummary::fullName, BusquedaPorNombre.porNombre())
                        .thenComparing(perfil -> perfil.id().value()))
                .toList();
        long total = coinciden.size();
        long desde = (long) pagina * tamano;
        List<UserSummary> deLaPagina = desde >= total ? List.of()
                : coinciden.subList((int) desde, (int) Math.min(total, desde + tamano));
        return new PaginaDePersonas(armar(deLaPagina, ahora), pagina, tamano, total);
    }

    /** Aprendices activos, que estén en el programa y no sean de prueba. */
    private List<UserSummary> padron() {
        Set<UserId> enElPrograma = new HashSet<>(participaciones.participantesInscritosActivos());
        List<UserSummary> candidatos = usuarios.aprendicesActivos().stream()
                .filter(perfil -> perfil.role() == UserRole.TRAINEE && enElPrograma.contains(perfil.id()))
                .toList();
        Set<UserId> deLaPrueba = pruebas.dePruebaEntre(candidatos.stream().map(UserSummary::id).toList());
        return candidatos.stream().filter(perfil -> !deLaPrueba.contains(perfil.id())).toList();
    }

    private List<PersonaDelPrograma> armar(List<UserSummary> pagina, Instant ahora) {
        if (pagina.isEmpty()) {
            return List.of();
        }
        Set<UserId> ids = new HashSet<>();
        pagina.forEach(perfil -> ids.add(perfil.id()));
        Map<UserId, List<GrupoDeLaPersona>> gruposPorPersona = gruposDe(ids, ahora);
        Map<UserId, ParticipacionPrograma> programas = new HashMap<>();
        pagina.forEach(perfil -> participaciones.deParticipante(perfil.id()).ifPresent(p -> programas.put(perfil.id(), p)));
        Map<UserId, GrupoDeLaPersona> elegidos = new HashMap<>();
        for (UserSummary perfil : pagina) {
            ParticipacionPrograma programa = programas.get(perfil.id());
            GrupoDeLaPersona.elegir(gruposPorPersona.getOrDefault(perfil.id(), List.of()),
                    programa == null ? null : programa.celulaId()).ifPresent(g -> elegidos.put(perfil.id(), g));
        }
        Map<UserId, UserSummary> mentores = usuarios.findByIds(elegidos.values().stream()
                .map(GrupoDeLaPersona::mentorId).filter(Objects::nonNull).distinct().toList());
        List<PersonaDelPrograma> personas = new ArrayList<>();
        for (UserSummary perfil : pagina) {
            GrupoDeLaPersona grupo = elegidos.get(perfil.id());
            ParticipacionPrograma programa = programas.get(perfil.id());
            UserSummary mentor = grupo == null || !grupo.conMentor() ? null : mentores.get(grupo.mentorId());
            personas.add(new PersonaDelPrograma(perfil.id().value(), perfil.fullName(), perfil.avatarUrl(),
                    grupo == null ? null : grupo.grupoId(), grupo == null ? null : grupo.nombre(),
                    mentor == null ? null : mentor.fullName(), diaDelPrograma(programa)));
        }
        return personas;
    }

    /** Null mientras no eligió su Día 1: antes de eso el día que guarda la base no es un día del programa. */
    private static Integer diaDelPrograma(ParticipacionPrograma programa) {
        return programa == null || !programa.activado() ? null : programa.diaPrograma();
    }

    /** Todos los grupos que corren hoy (recepción y sin mentor incluidos) en los que figuran estas personas. */
    private Map<UserId, List<GrupoDeLaPersona>> gruposDe(Set<UserId> personas, Instant ahora) {
        Map<UserId, List<GrupoDeLaPersona>> porPersona = new HashMap<>();
        for (GrupoConAprendices grupo : acompanamiento.gruposOperativos(ahora)) {
            GrupoDeLaPersona suyo = new GrupoDeLaPersona(grupo.grupoId(), grupo.nombre(), grupo.mentorId(), grupo.recepcion());
            for (UserId aprendiz : grupo.aprendices()) {
                if (personas.contains(aprendiz)) {
                    porPersona.computeIfAbsent(aprendiz, id -> new ArrayList<>()).add(suyo);
                }
            }
        }
        return porPersona;
    }

    private boolean seMuestra(UserSummary perfil) {
        return perfil.role() == UserRole.TRAINEE && perfil.status().allowsAccess() && !pruebas.esDePrueba(perfil.id());
    }

    private static NoSuchElementException noEncontrada() {
        return new NoSuchElementException("Esa persona no esta en el programa");
    }

    /**
     * A quién se mira y desde qué grupo: lo que la ficha, el Radar, los huecos y los cierres necesitan para leer.
     *
     * @param grupo null si la persona no está en ningún grupo
     */
    record Mirada(Miembro miembro, GrupoDeLaPersona grupo) {

        /** La mirada que ya tenía la ficha por grupo: ese grupo regular, con su mentor. */
        static Mirada deUnGrupo(Miembro miembro, GrupoAcompanado grupo) {
            return new Mirada(miembro, new GrupoDeLaPersona(grupo.grupoId(), grupo.nombre(), grupo.mentorId(), false));
        }

        UserId personaId() {
            return miembro.perfil().id();
        }

        UUID grupoId() {
            return grupo == null ? null : grupo.grupoId();
        }

        /** El mentor al que se le puede avisar; null si no hay (sin grupo, recepción o grupo sin mentor). */
        UserId mentorId() {
            return grupo != null && grupo.conMentor() ? grupo.mentorId() : null;
        }
    }
}
