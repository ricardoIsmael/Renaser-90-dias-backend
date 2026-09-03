package com.renaser.os.academy.application.services;

import com.renaser.os.academy.api.LeccionesVisiblesFinder;
import com.renaser.os.academy.application.ports.out.curso.LoadCursoPort;
import com.renaser.os.academy.application.ports.out.curso.LoadLeccionPort;
import com.renaser.os.academy.application.ports.out.curso.LoadSeccionCursoPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.ProgresoParticipanteAcademy;
import com.renaser.os.academy.application.ports.out.participante.ConsultarProgresoParticipanteAcademyPort.RolParticipante;
import com.renaser.os.academy.domain.model.curso.Curso;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.SeccionCurso;
import com.renaser.os.academy.domain.model.curso.SeccionCursoId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementación real de {@link LeccionesVisiblesFinder} (contrato público de `academy`).
 * Reutiliza EXACTAMENTE la misma regla de dos niveles que {@code CatalogoAcademyService}
 * ya aplica al armar el árbol de un curso — {@link Curso#visibleEnCatalogoPara} y, dentro
 * de un curso visible, {@link SeccionCurso#visibleEnCatalogoPara} — pero la resuelve para
 * TODO el catálogo de una sola pasada en vez de curso por curso.
 *
 * <p><b>Por qué 3 consultas y no N.</b> {@link LoadCursoPort#listarTodos()},
 * {@link LoadSeccionCursoPort#listarTodas()} y {@link LoadLeccionPort#listarIdentificadores()}
 * traen TODO el catálogo en una sola consulta cada una; el filtrado por visibilidad ocurre
 * después, en memoria. La alternativa obvia — por cada curso visible, pedir sus secciones y
 * lecciones con {@code porCurso(cursoId)} — dispara una consulta por curso: exactamente el
 * patrón N+1 que {@code ContarRegistrosDiariosHabitsPort} documenta y que este método evita
 * a propósito (su primer consumidor, {@code rag}, llama esto en cada pregunta a Renasia).
 */
@Service
public class LeccionesVisiblesAcademyService implements LeccionesVisiblesFinder {

    private final LoadCursoPort loadCursoPort;
    private final LoadSeccionCursoPort loadSeccionCursoPort;
    private final LoadLeccionPort loadLeccionPort;
    private final ConsultarProgresoParticipanteAcademyPort progresoPort;

    public LeccionesVisiblesAcademyService(LoadCursoPort loadCursoPort, LoadSeccionCursoPort loadSeccionCursoPort,
                                            LoadLeccionPort loadLeccionPort,
                                            ConsultarProgresoParticipanteAcademyPort progresoPort) {
        this.loadCursoPort = loadCursoPort;
        this.loadSeccionCursoPort = loadSeccionCursoPort;
        this.loadLeccionPort = loadLeccionPort;
        this.progresoPort = progresoPort;
    }

    @Override
    public Set<String> leccionesVisiblesPara(UserId actorId) {
        Optional<ProgresoParticipanteAcademy> progresoOpt = progresoPort.deParticipante(actorId);
        if (progresoOpt.isEmpty() || progresoOpt.get().suspendido()) {
            return Set.of();
        }
        ProgresoParticipanteAcademy progreso = progresoOpt.get();
        UserRole rol = aUserRole(progreso.rol());
        Integer programDay = rol == UserRole.TRAINEE ? progreso.diaPrograma() : null;

        Set<CursoId> cursosVisibles = loadCursoPort.listarTodos().stream()
                .filter(curso -> curso.visibleEnCatalogoPara(rol, programDay))
                .map(Curso::id)
                .collect(Collectors.toSet());
        if (cursosVisibles.isEmpty()) {
            return Set.of();
        }

        Set<SeccionCursoId> seccionesVisibles = loadSeccionCursoPort.listarTodas().stream()
                .filter(seccion -> cursosVisibles.contains(seccion.cursoId())
                        && seccion.visibleEnCatalogoPara(rol, programDay))
                .map(SeccionCurso::id)
                .collect(Collectors.toSet());

        return loadLeccionPort.listarIdentificadores().stream()
                .filter(leccion -> cursosVisibles.contains(leccion.cursoId()))
                .filter(leccion -> leccion.seccionId() == null || seccionesVisibles.contains(leccion.seccionId()))
                .map(leccion -> leccion.id().value())
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Espejo de `RolUsuarioJpa`/mapeos análogos en `rocks`/`phasecontracts`/`CatalogoAcademyService`. */
    private static UserRole aUserRole(RolParticipante rol) {
        return switch (rol) {
            case ALCHEMIST -> UserRole.ALCHEMIST;
            case ADMIN -> UserRole.ADMIN;
            case MENTOR_LEAD -> UserRole.MENTOR_LEAD;
            case MENTOR -> UserRole.MENTOR;
            case TRAINEE -> UserRole.TRAINEE;
        };
    }
}
