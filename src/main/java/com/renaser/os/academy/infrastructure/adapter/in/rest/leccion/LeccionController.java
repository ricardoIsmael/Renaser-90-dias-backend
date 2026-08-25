package com.renaser.os.academy.infrastructure.adapter.in.rest.leccion;

import com.renaser.os.academy.application.ports.in.leccion.CompletarLeccionUseCase;
import com.renaser.os.academy.application.ports.in.leccion.ConsultarLeccionUseCase;
import com.renaser.os.academy.application.ports.in.leccion.ConsultarMotivoBloqueoLeccionUseCase;
import com.renaser.os.academy.application.ports.in.leccion.DescompletarLeccionUseCase;
import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.academy.infrastructure.adapter.in.rest.curso.MotivoBloqueoResponse;
import com.renaser.os.shared.domain.UserId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Lecciones — lado alumno. Espejo de `/api/v1/lecciones/**` del repo viejo. */
@RestController
@RequestMapping("/api/v1/lecciones")
public class LeccionController {

    private final ConsultarLeccionUseCase leccionUseCase;
    private final ConsultarMotivoBloqueoLeccionUseCase motivoBloqueoUseCase;
    private final CompletarLeccionUseCase completarUseCase;
    private final DescompletarLeccionUseCase descompletarUseCase;

    public LeccionController(ConsultarLeccionUseCase leccionUseCase,
                              ConsultarMotivoBloqueoLeccionUseCase motivoBloqueoUseCase,
                              CompletarLeccionUseCase completarUseCase,
                              DescompletarLeccionUseCase descompletarUseCase) {
        this.leccionUseCase = leccionUseCase;
        this.motivoBloqueoUseCase = motivoBloqueoUseCase;
        this.completarUseCase = completarUseCase;
        this.descompletarUseCase = descompletarUseCase;
    }

    @GetMapping("/{id}")
    public LeccionDetalleResponse leccion(@RequestHeader("X-Actor-Id") String actorId,
                                           @PathVariable("id") String leccionId) {
        return LeccionDetalleResponse.from(leccionUseCase.leccion(UserId.of(actorId), LeccionId.of(leccionId)));
    }

    @GetMapping("/{id}/preview")
    public MotivoBloqueoResponse preview(@RequestHeader("X-Actor-Id") String actorId,
                                          @PathVariable("id") String leccionId) {
        return MotivoBloqueoResponse.from(motivoBloqueoUseCase.motivo(UserId.of(actorId), LeccionId.of(leccionId)));
    }

    /**
     * Reemplaza la escritura directa que la app hacia hoy contra
     * `leccion_progreso` (`src/services/cursos.ts: marcarLeccionCompletada`) —
     * cambio de release coordinado, ver `docs/MODULO_ACADEMY.md` §6.
     */
    @PostMapping("/{id}/complete")
    public CompletarLeccionResponse completar(@RequestHeader("X-Actor-Id") String actorId,
                                               @PathVariable("id") String leccionId) {
        return CompletarLeccionResponse.from(completarUseCase.completar(UserId.of(actorId), LeccionId.of(leccionId)));
    }

    /**
     * Inverso de {@code completar} — reemplaza la escritura directa que la app
     * hacia hoy contra `leccion_progreso` (`src/services/cursos.ts:
     * desmarcarLeccion` del repo RN), ver `docs/MODULO_ACADEMY.md` §5, AC-16.
     */
    @DeleteMapping("/{id}/complete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void descompletar(@RequestHeader("X-Actor-Id") String actorId,
                              @PathVariable("id") String leccionId) {
        descompletarUseCase.descompletar(UserId.of(actorId), LeccionId.of(leccionId));
    }
}
