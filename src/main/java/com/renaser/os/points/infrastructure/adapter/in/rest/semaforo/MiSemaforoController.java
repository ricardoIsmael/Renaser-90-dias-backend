package com.renaser.os.points.infrastructure.adapter.in.rest.semaforo;

import com.renaser.os.points.application.ports.in.semaforo.ConsultarMiSemaforoUseCase;
import com.renaser.os.points.application.ports.in.semaforo.PausarSemaforoUseCase;
import com.renaser.os.points.application.ports.in.semaforo.PausarSemaforoUseCase.PausarSemaforoCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * El semáforo de cumplimiento de la propia persona (D-168): {@code GET /api/v1/me/semaforo} y la
 * pausa del staff. Autoconsulta pura: no hay id ajeno en la petición.
 */
@RestController
@RequestMapping("/api/v1/me/semaforo")
public class MiSemaforoController {

    private final ConsultarMiSemaforoUseCase consultarMiSemaforo;
    private final PausarSemaforoUseCase pausarSemaforo;

    public MiSemaforoController(ConsultarMiSemaforoUseCase consultarMiSemaforo, PausarSemaforoUseCase pausarSemaforo) {
        this.consultarMiSemaforo = consultarMiSemaforo;
        this.pausarSemaforo = pausarSemaforo;
    }

    @RequiresPermission(value = Permission.USE_APP,
            scope = "autoconsulta: ConsultaDelSemaforoService exige cuenta activa")
    @GetMapping
    public DetalleDelSemaforoResponse consultar(@ActorAutenticado UserId actorId,
                                                @RequestParam(defaultValue = "8") int semanas) {
        return DetalleDelSemaforoResponse.de(consultarMiSemaforo.consultar(actorId, semanas));
    }

    @RequiresPermission(value = Permission.TRACK_PROGRAM_AS_STAFF,
            scope = "autoconsulta: PausaDelSemaforoService exige staff activo con programa propio")
    @PutMapping("/pausa")
    public DetalleDelSemaforoResponse pausar(@ActorAutenticado UserId actorId,
                                             @RequestBody @Valid PausarSemaforoRequest request) {
        return DetalleDelSemaforoResponse.de(pausarSemaforo.pausar(new PausarSemaforoCommand(actorId, request.hasta())));
    }

    @RequiresPermission(value = Permission.TRACK_PROGRAM_AS_STAFF,
            scope = "autoconsulta: PausaDelSemaforoService exige staff activo con programa propio")
    @DeleteMapping("/pausa")
    public DetalleDelSemaforoResponse reanudar(@ActorAutenticado UserId actorId) {
        return DetalleDelSemaforoResponse.de(pausarSemaforo.reanudar(actorId));
    }

    /** @param hasta último día pausado (fecha local, hoy o después) */
    public record PausarSemaforoRequest(@NotNull LocalDate hasta) {
    }
}
