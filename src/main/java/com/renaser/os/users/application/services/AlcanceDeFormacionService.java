package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.ObjetoDeOtraFormacionException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort.Pertenencia;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance.Asignacion;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * El punto único de «la formación de la persona» (spec 022, D-295): aplica {@link ReglaDeAlcance} a lo que la base dice de quien actúa.
 * Una consulta por PK por llamada.
 */
@Service
class AlcanceDeFormacionService implements AlcanceDeFormacionFinder {

    private final CargarPertenenciaPort pertenencias;

    AlcanceDeFormacionService(CargarPertenenciaPort pertenencias) {
        this.pertenencias = pertenencias;
    }

    @Override
    public AlcanceDeFormacion de(UserId actor) {
        return pertenencias.de(actor)
                .map(p -> ReglaDeAlcance.alcanceDe(p.rol(), p.deLaParticipacion(), p.asignadas()))
                .orElseGet(AlcanceDeFormacion::ninguna);
    }

    @Override
    public Optional<Formacion> formacionDe(UserId persona) {
        return pertenencias.de(persona).flatMap(this::formacionDe);
    }

    @Override
    public Set<Formacion> formacionesDe(UserId persona) {
        return pertenencias.de(persona).map(this::formacionesDe).orElse(Set.of());
    }

    @Override
    public Set<UserId> dentroDelAlcance(Collection<UserId> personas, AlcanceDeFormacion alcance) {
        return personas.isEmpty() ? Set.of() : pertenencias.dentroDelAlcance(personas, alcance);
    }

    @Override
    public void exigirQueLaVea(UserId actor, UserId objetivo) {
        AlcanceDeFormacion alcance = de(actor);
        if (alcance.esTodas()) {
            return;
        }
        Set<Formacion> delObjetivo = pertenencias.de(objetivo).map(this::formacionesDe).orElse(Set.of());
        if (delObjetivo.stream().noneMatch(alcance::incluye)) {
            throw new ObjetoDeOtraFormacionException();
        }
    }

    @Override
    public void exigirQueLaVea(UserId actor, Formacion formacionDelObjeto) {
        de(actor).exigirQueIncluya(formacionDelObjeto);
    }

    private Optional<Formacion> formacionDe(Pertenencia p) {
        if (ReglaDeAlcance.esPersonalConFormaciones(p.rol())) {
            return ReglaDeAlcance.formacionActiva(p.asignadas());
        }
        return Optional.ofNullable(p.deLaParticipacion());
    }

    /** Todas las formaciones a las que pertenece: la del aprendiz; las asignadas del personal; la de su programa si Administración lo hace. */
    private Set<Formacion> formacionesDe(Pertenencia p) {
        Set<Formacion> formaciones = EnumSet.noneOf(Formacion.class);
        if (p.deLaParticipacion() != null) {
            formaciones.add(p.deLaParticipacion());
        } else if (p.rol() == UserRole.TRAINEE) {
            formaciones.add(Formacion.POR_DEFECTO);
        }
        p.asignadas().stream().map(Asignacion::formacion).forEach(formaciones::add);
        return formaciones;
    }
}
