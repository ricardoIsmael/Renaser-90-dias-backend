package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.GruposDelPersonalFinder;
import com.renaser.os.users.api.GruposDelPersonalFinder.GrupoDelPersonal;
import com.renaser.os.users.application.ports.in.formacion.AsignarFormacionesDelPersonalUseCase;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort;
import com.renaser.os.users.application.ports.out.formacion.CargarPertenenciaPort.Pertenencia;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import com.renaser.os.users.application.ports.out.formacion.FormacionesDelPersonalPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance;
import com.renaser.os.users.domain.model.formacion.ReglaDeAlcance.Asignacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Asigna el personal a una o varias formaciones (spec 022, FR-007, FR-009, FR-011). El recurso (la persona) se carga PRIMERO y el guard de
 * Administración después (E-42). Rige en el siguiente pedido de la persona (Q11-A): su alcance se lee de la base en cada pedido.
 */
@Service
class AsignacionDeFormacionesDelPersonalService implements AsignarFormacionesDelPersonalUseCase {

    private final RequireAdminGuard requireAdminGuard;
    private final CargarPertenenciaPort pertenencias;
    private final FormacionesDelPersonalPort personal;
    private final GruposDelPersonalFinder grupos;
    private final CatalogoDeFormacionesPort catalogo;
    private final LoadParticipacionProgramaPort participaciones;
    private final SaveParticipacionProgramaPort guardarParticipacion;
    private final Clock clock;

    AsignacionDeFormacionesDelPersonalService(RequireAdminGuard requireAdminGuard, CargarPertenenciaPort pertenencias,
                                              FormacionesDelPersonalPort personal, GruposDelPersonalFinder grupos,
                                              CatalogoDeFormacionesPort catalogo, LoadParticipacionProgramaPort participaciones,
                                              SaveParticipacionProgramaPort guardarParticipacion, Clock clock) {
        this.requireAdminGuard = requireAdminGuard;
        this.pertenencias = pertenencias;
        this.personal = personal;
        this.grupos = grupos;
        this.catalogo = catalogo;
        this.participaciones = participaciones;
        this.guardarParticipacion = guardarParticipacion;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void asignar(AsignarFormacionesCommand command) {
        Pertenencia actual = pertenencias.de(command.persona())
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + command.persona()));
        requireAdminGuard.requireAdminActivo(command.actor());
        if (!ReglaDeAlcance.esPersonalConFormaciones(actual.rol())) {
            throw new IllegalStateException("Solo el personal (mentor, guía de aprendiz y líder de mentores) trabaja en formaciones asignadas.");
        }
        Set<Formacion> nuevas = elegidas(command);
        exigirQueNoDejeUnGrupo(command.persona(), actual, nuevas);
        personal.reemplazar(command.persona(), nuevas, laActiva(command, actual, nuevas), command.actor());
        moverSuProgramaSinEmpezar(command.persona(), nuevas);
    }

    /**
     * Spec 025-6 A (E2E P3-L): el programa propio del personal vive en la primera de sus formaciones por orden. Si su programa todavía no empezó (no
     * eligió su Día 1) y estaba en una formación que ya no tiene, pasa a esa primera: de lo contrario seguía viendo el catálogo de hábitos de una
     * formación que Administración le quitó. Uno que ya empezó se deja como está (no se le reinicia un programa en marcha), y el que tiene grupo
     * también: mover eso es del traslado de un aprendiz, que limpia el grupo y los puntos, y el personal no es un aprendiz.
     */
    private void moverSuProgramaSinEmpezar(UserId persona, Set<Formacion> nuevas) {
        ParticipacionPrograma programa = participaciones.byParticipanteId(persona).orElse(null);
        if (programa == null || programa.estaActivado() || programa.celulaId() != null || nuevas.contains(programa.formacion())) {
            return;
        }
        Formacion primera = nuevas.stream().sorted().findFirst().orElseThrow();
        programa.trasladarA(primera, catalogo.de(primera).duracionPorDefecto(), clock);
        guardarParticipacion.save(programa);
    }

    private static Set<Formacion> elegidas(AsignarFormacionesCommand command) {
        if (command.formaciones() == null || command.formaciones().isEmpty()) {
            throw new IllegalArgumentException("Elige al menos una formación para esta persona.");
        }
        return EnumSet.copyOf(command.formaciones());
    }

    /** FR-011: no se le quita una formación donde lidera un grupo. */
    private void exigirQueNoDejeUnGrupo(UserId persona, Pertenencia actual, Set<Formacion> nuevas) {
        Set<Formacion> quitadas = EnumSet.noneOf(Formacion.class);
        actual.asignadas().stream().map(Asignacion::formacion).filter(f -> !nuevas.contains(f)).forEach(quitadas::add);
        if (quitadas.isEmpty()) {
            return;
        }
        for (GrupoDelPersonal grupo : grupos.queLidera(persona)) {
            if (quitadas.contains(grupo.formacion())) {
                throw new IllegalStateException("No se puede quitar la formación «" + catalogo.de(grupo.formacion()).nombre()
                        + "»: esta persona lidera el grupo «" + grupo.nombre() + "». Reasigna ese grupo primero.");
            }
        }
    }

    private static Formacion laActiva(AsignarFormacionesCommand command, Pertenencia actual, Set<Formacion> nuevas) {
        if (command.activa() != null) {
            if (!nuevas.contains(command.activa())) {
                throw new IllegalArgumentException("La formación activa tiene que ser una de las asignadas.");
            }
            return command.activa();
        }
        return ReglaDeAlcance.formacionActiva(actual.asignadas()).filter(nuevas::contains)
                .orElseGet(() -> nuevas.stream().sorted().findFirst().orElseThrow());
    }
}
