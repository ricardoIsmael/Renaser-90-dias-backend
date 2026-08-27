package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.application.ports.in.celula.ActualizarCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ActualizarCelulaUseCase.ActualizarCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.AsignarMentorCelulaUseCase.AsignarMentorCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.ConsultarCandidatosCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.ConsultarDashboardCelulasUseCase;
import com.renaser.os.community.application.ports.in.celula.CrearCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.CrearCelulaUseCase.CrearCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.EliminarCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.EliminarCelulaUseCase.EliminarCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.ProgramarSesionCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.ProgramarSesionCelulaUseCase.ProgramarSesionCelulaCommand;
import com.renaser.os.community.application.ports.in.celula.QuitarMentorCelulaUseCase;
import com.renaser.os.community.application.ports.in.celula.QuitarMentorCelulaUseCase.QuitarMentorCelulaCommand;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/cells")
public class CelulaAdminController {

    private final CrearCelulaUseCase crearUseCase;
    private final ActualizarCelulaUseCase actualizarUseCase;
    private final AsignarMentorCelulaUseCase asignarMentorUseCase;
    private final QuitarMentorCelulaUseCase quitarMentorUseCase;
    private final ProgramarSesionCelulaUseCase programarSesionUseCase;
    private final EliminarCelulaUseCase eliminarUseCase;
    private final ConsultarCelulasUseCase consultarUseCase;
    private final ConsultarDashboardCelulasUseCase dashboardUseCase;
    private final ConsultarCandidatosCelulaUseCase candidatosUseCase;

    public CelulaAdminController(CrearCelulaUseCase crearUseCase, ActualizarCelulaUseCase actualizarUseCase,
                                  AsignarMentorCelulaUseCase asignarMentorUseCase,
                                  QuitarMentorCelulaUseCase quitarMentorUseCase,
                                  ProgramarSesionCelulaUseCase programarSesionUseCase,
                                  EliminarCelulaUseCase eliminarUseCase, ConsultarCelulasUseCase consultarUseCase,
                                  ConsultarDashboardCelulasUseCase dashboardUseCase,
                                  ConsultarCandidatosCelulaUseCase candidatosUseCase) {
        this.crearUseCase = crearUseCase;
        this.actualizarUseCase = actualizarUseCase;
        this.asignarMentorUseCase = asignarMentorUseCase;
        this.quitarMentorUseCase = quitarMentorUseCase;
        this.programarSesionUseCase = programarSesionUseCase;
        this.eliminarUseCase = eliminarUseCase;
        this.consultarUseCase = consultarUseCase;
        this.dashboardUseCase = dashboardUseCase;
        this.candidatosUseCase = candidatosUseCase;
    }

    /** #25: panel admin cross-cohorte — ruta propia (no {@code GET /admin/cells} a
     * secas) para no romper el contrato ya vigente de {@link #listarPorCohorte}, que
     * exige {@code cohortId}. */
    @GetMapping("/dashboard")
    public List<CelulaDashboardResponse> dashboard(@RequestHeader("X-Actor-Id") String actorId) {
        return dashboardUseCase.dashboard(UserId.of(actorId)).stream().map(CelulaDashboardResponse::from).toList();
    }

    /** #25: mentores ACTIVOS sin celula — candidatos del selector "Asignar mentor". */
    @GetMapping("/mentores-disponibles")
    public List<MentorCandidatoResponse> mentoresDisponibles(@RequestHeader("X-Actor-Id") String actorId) {
        return candidatosUseCase.mentoresDisponibles(UserId.of(actorId)).stream().map(MentorCandidatoResponse::from)
                .toList();
    }

    /** #25: TODOS los mentores ACTIVOS, marcando con {@code cellId} a quien ya lidera una. */
    @GetMapping("/mentores")
    public List<MentorCandidatoResponse> mentores(@RequestHeader("X-Actor-Id") String actorId) {
        return candidatosUseCase.mentores(UserId.of(actorId)).stream().map(MentorCandidatoResponse::from).toList();
    }

    /** #25: aprendices ACTIVOS sin celula (alcance global, ver javadoc del caso de uso). */
    @GetMapping("/aprendices-disponibles")
    public List<AprendizCandidatoResponse> aprendicesDisponibles(@RequestHeader("X-Actor-Id") String actorId) {
        return candidatosUseCase.aprendicesDisponibles(UserId.of(actorId)).stream()
                .map(AprendizCandidatoResponse::from).toList();
    }

    @GetMapping
    public List<CelulaResponse> listarPorCohorte(@RequestHeader("X-Actor-Id") String actorId,
                                                  @RequestParam UUID cohortId) {
        return consultarUseCase.listarPorCohorte(UserId.of(actorId), CohorteId.of(cohortId)).stream()
                .map(CelulaResponse::from).toList();
    }

    @GetMapping("/{id}")
    public CelulaDetalleResponse obtener(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id) {
        return CelulaDetalleResponse.from(consultarUseCase.obtener(UserId.of(actorId), CelulaId.of(id)));
    }

    @PostMapping
    public ResponseEntity<CelulaDetalleResponse> crear(@RequestHeader("X-Actor-Id") String actorId,
                                                         @RequestBody @Valid CrearCelulaRequest request) {
        var celula = crearUseCase.crear(new CrearCelulaCommand(UserId.of(actorId), request.name(),
                CohorteId.of(request.cohortId()), request.videoCallUrl()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CelulaDetalleResponse.from(consultarUseCase.obtener(UserId.of(actorId), celula.id())));
    }

    @PatchMapping("/{id}")
    public CelulaDetalleResponse actualizar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id,
                                             @RequestBody ActualizarCelulaRequest request) {
        actualizarUseCase.actualizar(new ActualizarCelulaCommand(UserId.of(actorId), CelulaId.of(id), request.name(),
                request.videoCallUrl(), true));
        return CelulaDetalleResponse.from(consultarUseCase.obtener(UserId.of(actorId), CelulaId.of(id)));
    }

    @PutMapping("/{id}/mentor")
    public CelulaDetalleResponse asignarMentor(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id,
                                                @RequestBody @Valid AsignarMentorRequest request) {
        asignarMentorUseCase.asignar(new AsignarMentorCelulaCommand(UserId.of(actorId), CelulaId.of(id),
                UserId.of(request.leaderUserId())));
        return CelulaDetalleResponse.from(consultarUseCase.obtener(UserId.of(actorId), CelulaId.of(id)));
    }

    @DeleteMapping("/{id}/mentor")
    public CelulaDetalleResponse quitarMentor(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id) {
        quitarMentorUseCase.quitar(new QuitarMentorCelulaCommand(UserId.of(actorId), CelulaId.of(id)));
        return CelulaDetalleResponse.from(consultarUseCase.obtener(UserId.of(actorId), CelulaId.of(id)));
    }

    @PostMapping("/{id}/session")
    public CelulaDetalleResponse programarSesion(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id,
                                                  @RequestBody @Valid ProgramarSesionRequest request) {
        programarSesionUseCase.programar(new ProgramarSesionCelulaCommand(UserId.of(actorId), CelulaId.of(id),
                request.scheduledAt()));
        return CelulaDetalleResponse.from(consultarUseCase.obtener(UserId.of(actorId), CelulaId.of(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@RequestHeader("X-Actor-Id") String actorId, @PathVariable UUID id) {
        eliminarUseCase.eliminar(new EliminarCelulaCommand(UserId.of(actorId), CelulaId.of(id)));
        return ResponseEntity.noContent().build();
    }
}
