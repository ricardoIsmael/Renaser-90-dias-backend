package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase.SubirEvidenciaRegistroCommand;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Actor resuelto por header `X-Actor-Id` (temporal, D-29 de `users` — sin
 * autenticacion real todavia por B-2, mismo patron que `points`/`phasecontracts`/`support`).
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
    @GetMapping("/today")
    public List<RegistroHabitoConCatalogoResponse> hoy(@RequestHeader("X-Actor-Id") String actorId) {
        UserId actor = UserId.of(actorId);
        return consultarTracksDelDiaUseCase.consultar(actor, actor, LocalDate.now())
                .stream().map(RegistroHabitoConCatalogoResponse::from).toList();
    }

    @PostMapping("/{id}/complete")
    public RegistroHabitoResponse completar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable String id,
                                             @RequestBody @Valid CompletarRegistroRequest request) {
        var registro = completarRegistroUseCase.completar(new CompletarRegistroCommand(UserId.of(actorId),
                RegistroHabitoId.of(java.util.UUID.fromString(id)), request.respuestaTexto(),
                request.calificacionProductividad()));
        return RegistroHabitoResponse.from(registro);
    }

    /** D-H6: sube la evidencia de un registro diario, delegando en `evidence.api.RegistrarEvidenciaPort`. */
    @PostMapping("/{id}/evidence")
    public EvidenciaRegistroResponse subirEvidencia(@RequestHeader("X-Actor-Id") String actorId,
                                                      @PathVariable String id,
                                                      @RequestBody @Valid SubirEvidenciaRegistroRequest request) {
        var evidencia = subirEvidenciaUseCase.subir(new SubirEvidenciaRegistroCommand(UserId.of(actorId),
                RegistroHabitoId.of(java.util.UUID.fromString(id)), TipoEvidencia.valueOf(request.tipo()),
                request.bucket(), request.rutaStorage(), request.contenidoTexto(), request.timestampExif(),
                request.gpsLat(), request.gpsLng()));
        return EvidenciaRegistroResponse.from(evidencia);
    }
}
