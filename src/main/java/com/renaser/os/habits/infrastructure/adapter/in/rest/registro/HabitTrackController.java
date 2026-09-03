package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase.SubirEvidenciaRegistroCommand;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Actor resuelto desde la sesion por {@code @ActorAutenticado}, con respaldo por el header
 * temporal `X-Actor-Id` (D-29 de `users`) mientras dure la migracion.
 * Autoservicio: el participante solo opera sobre sus propios tracks.
 */
@RestController
@RequestMapping("/api/v1/habit-tracks")
public class HabitTrackController {

    private final ConsultarTracksDelDiaConCatalogoUseCase consultarTracksDelDiaUseCase;
    private final CompletarRegistroUseCase completarRegistroUseCase;
    private final SubirEvidenciaRegistroUseCase subirEvidenciaUseCase;

    public HabitTrackController(ConsultarTracksDelDiaConCatalogoUseCase consultarTracksDelDiaUseCase,
                                 CompletarRegistroUseCase completarRegistroUseCase,
                                 SubirEvidenciaRegistroUseCase subirEvidenciaUseCase) {
        this.consultarTracksDelDiaUseCase = consultarTracksDelDiaUseCase;
        this.completarRegistroUseCase = completarRegistroUseCase;
        this.subirEvidenciaUseCase = subirEvidenciaUseCase;
    }

    /** Hueco #10: cada registro trae el catalogo resuelto (titulo/tipo/guia/horario) — sin N+1. */
    @RequiresPermission(value = Permission.USE_APP, scope = "opera sobre los habitos del propio actor")
    @GetMapping("/today")
    public List<RegistroHabitoConCatalogoResponse> hoy(@ActorAutenticado UserId actor) {
        return consultarTracksDelDiaUseCase.consultar(actor, actor, LocalDate.now())
                .stream().map(RegistroHabitoConCatalogoResponse::from).toList();
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "dueno del registro de habito")
    @PostMapping("/{id}/complete")
    public RegistroHabitoResponse completar(@ActorAutenticado UserId actor, @PathVariable String id,
                                             @RequestBody @Valid CompletarRegistroRequest request) {
        var registro = completarRegistroUseCase.completar(new CompletarRegistroCommand(actor,
                RegistroHabitoId.of(java.util.UUID.fromString(id)), request.respuestaTexto(),
                request.calificacionProductividad()));
        return RegistroHabitoResponse.from(registro);
    }

    /** D-H6: sube la evidencia de un registro diario, delegando en `evidence.api.RegistrarEvidenciaPort`. */
    @RequiresPermission(value = Permission.USE_APP, scope = "dueno del registro de habito")
    @PostMapping("/{id}/evidence")
    public EvidenciaRegistroResponse subirEvidencia(@ActorAutenticado UserId actor,
                                                      @PathVariable String id,
                                                      @RequestBody @Valid SubirEvidenciaRegistroRequest request) {
        var evidencia = subirEvidenciaUseCase.subir(new SubirEvidenciaRegistroCommand(actor,
                RegistroHabitoId.of(java.util.UUID.fromString(id)), TipoEvidencia.valueOf(request.tipo()),
                request.bucket(), request.rutaStorage(), request.contenidoTexto(), request.timestampExif(),
                request.gpsLat(), request.gpsLng()));
        return EvidenciaRegistroResponse.from(evidencia);
    }
}
