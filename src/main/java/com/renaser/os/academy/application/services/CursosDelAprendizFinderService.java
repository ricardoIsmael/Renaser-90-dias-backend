package com.renaser.os.academy.application.services;

import com.renaser.os.academy.api.CursosDelAprendizFinder;
import com.renaser.os.academy.application.ports.in.curso.ConsultarCursosBloqueadosUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase.CursoConProgreso;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.BloqueadoPorDia;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.MotivoBloqueoCurso;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase.NoBloqueado;
import com.renaser.os.academy.application.ports.in.leccion.ConsultarMotivoBloqueoLeccionUseCase;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Implementa {@link CursosDelAprendizFinder} (2026-09-23). Fachada delgada sobre los casos de uso
 * del catalogo del alumno: la visibilidad por rol, publicacion y dia de programa, y el criterio de
 * revelar solo el bloqueo por dia, siguen en {@code CatalogoAcademyService}. Aca solo se traduce
 * al contrato publico, sin la portada firmada (el chat no muestra imagenes).
 */
@Service
public class CursosDelAprendizFinderService implements CursosDelAprendizFinder {

    private final ConsultarMisCursosUseCase misCursosUseCase;
    private final ConsultarCursosBloqueadosUseCase bloqueadosUseCase;
    private final ConsultarMotivoBloqueoCursoUseCase motivoCursoUseCase;
    private final ConsultarMotivoBloqueoLeccionUseCase motivoLeccionUseCase;

    public CursosDelAprendizFinderService(ConsultarMisCursosUseCase misCursosUseCase,
                                          ConsultarCursosBloqueadosUseCase bloqueadosUseCase,
                                          ConsultarMotivoBloqueoCursoUseCase motivoCursoUseCase,
                                          ConsultarMotivoBloqueoLeccionUseCase motivoLeccionUseCase) {
        this.misCursosUseCase = misCursosUseCase;
        this.bloqueadosUseCase = bloqueadosUseCase;
        this.motivoCursoUseCase = motivoCursoUseCase;
        this.motivoLeccionUseCase = motivoLeccionUseCase;
    }

    @Override
    public CursosDelAprendiz cursosDe(UserId actorId) {
        List<CursoAccesible> accesibles = misCursosUseCase.misCursos(actorId).stream()
                .map(CursosDelAprendizFinderService::aAccesible)
                .toList();
        List<CursoBloqueado> bloqueados = bloqueadosUseCase.cursosBloqueados(actorId).stream()
                .map(b -> new CursoBloqueado(b.curso().id().value(), b.curso().titulo(), b.diaDesbloqueo(),
                        b.programDayActual()))
                .toList();
        return new CursosDelAprendiz(accesibles, bloqueados);
    }

    @Override
    public MotivoBloqueo motivoBloqueoCurso(UserId actorId, String cursoId) {
        return aMotivo(motivoCursoUseCase.motivo(actorId, CursoId.of(cursoId)));
    }

    @Override
    public MotivoBloqueo motivoBloqueoLeccion(UserId actorId, String leccionId) {
        return aMotivo(motivoLeccionUseCase.motivo(actorId, LeccionId.of(leccionId)));
    }

    private static CursoAccesible aAccesible(CursoConProgreso curso) {
        return new CursoAccesible(curso.curso().id().value(), curso.curso().titulo(),
                curso.progreso().totalLecciones(), curso.progreso().completadas());
    }

    static MotivoBloqueo aMotivo(MotivoBloqueoCurso motivo) {
        return switch (motivo) {
            case NoBloqueado ignored -> MotivoBloqueo.noBloqueado();
            case BloqueadoPorDia b -> new MotivoBloqueo(true, b.tituloBloqueado(), b.diaDesbloqueo(),
                    b.programDayActual());
        };
    }
}
