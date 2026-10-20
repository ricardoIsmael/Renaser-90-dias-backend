package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasDePruebaFinder;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Quiénes son «las personas de un grupo» para el líder (spec 005, FR-001 y FR-002): los aprendices del grupo y quien
 * lleva su propio programa de 90 días en él (su mentor o su guía), con la cuenta activa y que no sea de prueba.
 *
 * <p>Solo los grupos regulares con mentor vigente: la recepción y un grupo sin mentor son 404, no una lista vacía.
 * Una persona que hoy no está en el grupo también es 404 (RL-08): el id de otro grupo no abre su ficha desde acá.
 * Una cuenta suspendida o cerrada —que queda suspendida— no se muestra. Todo entra por APIs públicas.
 */
@Component
class PersonasDelGrupo {

    private final AcompanamientoFinder acompanamiento;
    private final ParticipacionProgramaFinder participaciones;
    private final UserSummaryFinder usuarios;
    private final CuentasDePruebaFinder pruebas;

    PersonasDelGrupo(AcompanamientoFinder acompanamiento, ParticipacionProgramaFinder participaciones,
                     UserSummaryFinder usuarios, CuentasDePruebaFinder pruebas) {
        this.acompanamiento = acompanamiento;
        this.participaciones = participaciones;
        this.usuarios = usuarios;
        this.pruebas = pruebas;
    }

    /** El grupo regular con mentor vigente. Cualquier otro —recepción, sin mentor, inexistente— es 404. */
    GrupoAcompanado requireGrupoConMentor(UUID grupoId, Instant ahora) {
        return acompanamiento.gruposConMentorVigente(ahora).stream()
                .filter(grupo -> grupo.grupoId().equals(grupoId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Grupo no encontrado"));
    }

    /** Quien acompaña el grupo y lleva su propio programa activado: entra a la lista sin color. */
    Optional<UserSummary> acompananteConPrograma(GrupoAcompanado grupo) {
        return miembroDelAcompanante(grupo).map(Miembro::perfil);
    }

    /** La persona pedida si hoy es del grupo; si no, 404. Va DESPUÉS del guard de rol (RL-08). */
    Miembro requireMiembro(GrupoAcompanado grupo, UserId personaId, Instant ahora) {
        if (personaId.equals(grupo.mentorId())) {
            return miembroDelAcompanante(grupo).orElseThrow(PersonasDelGrupo::noEncontrada);
        }
        if (!acompanamiento.aprendicesVigentes(grupo.grupoId(), ahora).contains(personaId)) {
            throw noEncontrada();
        }
        UserSummary perfil = usuarios.findById(personaId).filter(this::seMuestra).orElseThrow(PersonasDelGrupo::noEncontrada);
        ParticipacionPrograma participacion = participaciones.deParticipante(personaId)
                .orElseThrow(PersonasDelGrupo::noEncontrada);
        return new Miembro(perfil, participacion, false);
    }

    /** De estos ids, los de cuentas de prueba: nunca aparecen en una lista del líder (SC-006). */
    Set<UserId> dePrueba(Collection<UserId> ids) {
        return pruebas.dePruebaEntre(ids);
    }

    private Optional<Miembro> miembroDelAcompanante(GrupoAcompanado grupo) {
        UserId acompanante = grupo.mentorId();
        return participaciones.deParticipante(acompanante).filter(ParticipacionPrograma::activado)
                .flatMap(participacion -> usuarios.findById(acompanante).filter(this::seMuestra)
                        .map(perfil -> new Miembro(perfil, participacion, true)));
    }

    private boolean seMuestra(UserSummary perfil) {
        return perfil.status().allowsAccess() && !pruebas.esDePrueba(perfil.id());
    }

    private static NoSuchElementException noEncontrada() {
        return new NoSuchElementException("Esa persona no esta en ese grupo");
    }

    /**
     * Una persona del grupo con su perfil y su programa: lo que la ficha necesita para pedir su semana.
     *
     * @param acompanante true si es quien acompaña el grupo (su mentor o su guía) y lleva su propio programa: sin color
     */
    record Miembro(UserSummary perfil, ParticipacionPrograma participacion, boolean acompanante) {
    }
}
