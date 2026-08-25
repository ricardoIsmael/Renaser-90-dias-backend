package com.renaser.os.academy.application.services;

import com.renaser.os.academy.api.AccesoCursoFinder;
import com.renaser.os.academy.application.ports.out.asignacion.LoadAsignacionCursoPort;
import com.renaser.os.academy.application.ports.out.asignacion.LoadMiembroGrupoPort;
import com.renaser.os.academy.domain.model.asignacion.AsignacionCurso;
import com.renaser.os.academy.domain.model.asignacion.GrupoId;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementacion real de {@link AccesoCursoFinder} (contrato publico de
 * `academy`, ver `academy/api/AccesoCursoFinder.java`): asignacion vigente,
 * directa o por grupo. Unico dueño de `asignaciones_curso` — quien necesite
 * esta pregunta desde otro modulo (hoy: `calendar`, audiencia `CURSO`) pasa
 * por este puerto en vez de leer la tabla.
 *
 * <p><b>OJO:</b> esto es una pregunta DISTINTA de "puede este actor ver el
 * curso en su catalogo" ({@link com.renaser.os.academy.domain.model.curso.Curso#visibleEnCatalogoPara}).
 * Ver AC-01 en `Curso` y §4 de `docs/MODULO_ACADEMY.md`.
 */
@Service
public class AccesoCursoService implements AccesoCursoFinder {

    private final LoadAsignacionCursoPort loadAsignacionCursoPort;
    private final LoadMiembroGrupoPort loadMiembroGrupoPort;
    private final Clock clock;

    public AccesoCursoService(LoadAsignacionCursoPort loadAsignacionCursoPort,
                               LoadMiembroGrupoPort loadMiembroGrupoPort, Clock clock) {
        this.loadAsignacionCursoPort = loadAsignacionCursoPort;
        this.loadMiembroGrupoPort = loadMiembroGrupoPort;
        this.clock = clock;
    }

    @Override
    public Set<UserId> usuariosConAcceso(String cursoId) {
        List<AsignacionCurso> vigentes = asignacionesVigentes(cursoId);

        Set<UserId> directos = vigentes.stream()
                .filter(AsignacionCurso::esDirecta)
                .map(AsignacionCurso::usuarioId)
                .collect(Collectors.toSet());

        Set<GrupoId> grupoIds = vigentes.stream()
                .filter(a -> !a.esDirecta())
                .map(AsignacionCurso::grupoId)
                .collect(Collectors.toSet());
        Set<UserId> deGrupos = grupoIds.isEmpty() ? Set.of() : loadMiembroGrupoPort.usuariosDeGrupos(grupoIds);

        Set<UserId> resultado = new HashSet<>(directos);
        resultado.addAll(deGrupos);
        return resultado;
    }

    @Override
    public boolean tieneAcceso(UserId usuarioId, String cursoId) {
        return usuariosConAcceso(cursoId).contains(usuarioId);
    }

    private List<AsignacionCurso> asignacionesVigentes(String cursoId) {
        Instant ahora = clock.now();
        return loadAsignacionCursoPort.porCurso(CursoId.of(cursoId)).stream()
                .filter(a -> a.vigente(ahora))
                .toList();
    }
}
