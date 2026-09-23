package com.renaser.os.rag.infrastructure.adapter.out.academy;

import com.renaser.os.academy.api.CursosDelAprendizFinder;
import com.renaser.os.academy.api.CursosDelAprendizFinder.MotivoBloqueo;
import com.renaser.os.rag.application.ports.out.academia.ConsultarCursosPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

/**
 * Implementa {@link ConsultarCursosPort} delegando en {@code academy.api.CursosDelAprendizFinder}
 * (D-41). Traduccion campo a campo; el gate y el criterio de no revelar de mas son de {@code academy}.
 */
@Component
class ConsultarCursosAdapter implements ConsultarCursosPort {

    private final CursosDelAprendizFinder cursosFinder;

    ConsultarCursosAdapter(CursosDelAprendizFinder cursosFinder) {
        this.cursosFinder = cursosFinder;
    }

    @Override
    public CursosDelAprendiz cursosDe(UserId actorId) {
        CursosDelAprendizFinder.CursosDelAprendiz cursos = cursosFinder.cursosDe(actorId);
        return new CursosDelAprendiz(
                cursos.accesibles().stream()
                        .map(c -> new CursoAccesible(c.cursoId(), c.titulo(), c.totalLecciones(),
                                c.leccionesCompletadas()))
                        .toList(),
                cursos.bloqueados().stream()
                        .map(c -> new CursoBloqueado(c.cursoId(), c.titulo(), c.diaDesbloqueo(), c.diaProgramaActual()))
                        .toList());
    }

    @Override
    public Bloqueo bloqueoDeCurso(UserId actorId, String cursoId) {
        return aBloqueo(cursosFinder.motivoBloqueoCurso(actorId, cursoId));
    }

    @Override
    public Bloqueo bloqueoDeLeccion(UserId actorId, String leccionId) {
        return aBloqueo(cursosFinder.motivoBloqueoLeccion(actorId, leccionId));
    }

    private static Bloqueo aBloqueo(MotivoBloqueo motivo) {
        return new Bloqueo(motivo.bloqueado(), motivo.titulo(), motivo.diaDesbloqueo(), motivo.diaProgramaActual());
    }
}
