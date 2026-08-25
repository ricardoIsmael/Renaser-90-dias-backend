package com.renaser.os.academy.infrastructure.adapter.in.rest.curso;

import com.renaser.os.academy.application.ports.in.curso.ConsultarCursoDetalleUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarCursosBloqueadosUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMisCursosUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarMotivoBloqueoCursoUseCase;
import com.renaser.os.academy.application.ports.in.curso.ConsultarSeccionesCursoUseCase;
import com.renaser.os.academy.domain.model.curso.CursoId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Cursos — lado alumno. Espejo de `/api/v1/cursos/**` del repo viejo (`src/app/api/v1/cursos/**`).
 * Sin filtro JWT todavia: el actor llega por `X-Actor-Id` (bloqueante del usuario, ver CLAUDE.MD).
 */
@RestController
@RequestMapping("/api/v1/cursos")
public class CursoController {

    private final ConsultarMisCursosUseCase misCursosUseCase;
    private final ConsultarCursoDetalleUseCase detalleUseCase;
    private final ConsultarSeccionesCursoUseCase seccionesUseCase;
    private final ConsultarMotivoBloqueoCursoUseCase motivoBloqueoUseCase;
    private final ConsultarCursosBloqueadosUseCase cursosBloqueadosUseCase;

    public CursoController(ConsultarMisCursosUseCase misCursosUseCase, ConsultarCursoDetalleUseCase detalleUseCase,
                            ConsultarSeccionesCursoUseCase seccionesUseCase,
                            ConsultarMotivoBloqueoCursoUseCase motivoBloqueoUseCase,
                            ConsultarCursosBloqueadosUseCase cursosBloqueadosUseCase) {
        this.misCursosUseCase = misCursosUseCase;
        this.detalleUseCase = detalleUseCase;
        this.seccionesUseCase = seccionesUseCase;
        this.motivoBloqueoUseCase = motivoBloqueoUseCase;
        this.cursosBloqueadosUseCase = cursosBloqueadosUseCase;
    }

    @GetMapping
    public List<MiCursoResponse> misCursos(@RequestHeader("X-Actor-Id") String actorId) {
        return misCursosUseCase.misCursos(UserId.of(actorId)).stream().map(MiCursoResponse::from).toList();
    }

    /**
     * Reemplaza la RPC de Supabase {@code catalogo_cursos_bloqueados} (0018),
     * que la app llamaba directo (`src/services/cursos.ts: listarCursosBloqueados`)
     * — ver `docs/MODULO_ACADEMY.md` §5, decision AC-15. Ruta literal ANTES de
     * `/{id}` para que Spring la resuelva como segmento fijo, no como un id de
     * curso.
     */
    @GetMapping("/bloqueados")
    public List<CursoBloqueadoResponse> bloqueados(@RequestHeader("X-Actor-Id") String actorId) {
        return cursosBloqueadosUseCase.cursosBloqueados(UserId.of(actorId)).stream()
                .map(CursoBloqueadoResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public CursoDetalleResponse detalle(@RequestHeader("X-Actor-Id") String actorId, @PathVariable("id") String cursoId) {
        return CursoDetalleResponse.from(detalleUseCase.detalle(UserId.of(actorId), CursoId.of(cursoId)));
    }

    @GetMapping("/{id}/secciones")
    public List<SeccionConLeccionesResponse> secciones(@RequestHeader("X-Actor-Id") String actorId,
                                                         @PathVariable("id") String cursoId) {
        return seccionesUseCase.secciones(UserId.of(actorId), CursoId.of(cursoId)).stream()
                .map(SeccionConLeccionesResponse::from)
                .toList();
    }

    @GetMapping("/{id}/preview")
    public MotivoBloqueoResponse preview(@RequestHeader("X-Actor-Id") String actorId,
                                          @PathVariable("id") String cursoId) {
        return MotivoBloqueoResponse.from(motivoBloqueoUseCase.motivo(UserId.of(actorId), CursoId.of(cursoId)));
    }
}
