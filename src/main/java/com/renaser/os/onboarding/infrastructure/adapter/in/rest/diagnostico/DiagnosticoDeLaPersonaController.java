package com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico;

import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase.EstadoDeMiDiagnostico;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase.ResultadoDeTerminar;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase.ResumenDeMiDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.DiagnosticoIncompletoException;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.DiagnosticoResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.EstadoResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.GuardadasResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.GuardarRespuestasRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.ObjetivoResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.ResumenResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.TerminarResponse;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** El diagnóstico de la propia persona (spec 026, contrato §1). */
@RestController
@RequestMapping("/api/v1/diagnostico/mio")
@Funcionalidad("onboarding")
public class DiagnosticoDeLaPersonaController {

    private static final String SOLO_LAS_PROPIAS = "solo las respuestas del propio actor";

    private final DiagnosticoDeLaPersonaUseCase diagnostico;
    private final DiagnosticoDeLaPersonaFinder lecturas;

    public DiagnosticoDeLaPersonaController(DiagnosticoDeLaPersonaUseCase diagnostico, DiagnosticoDeLaPersonaFinder lecturas) {
        this.diagnostico = diagnostico;
        this.lecturas = lecturas;
    }

    @RequiresPermission(Permission.USE_APP)
    @SinInterruptor(motivo = "Hoy y Yo lo leen al entrar para saber si ofrecer el diagnóstico: apagar el onboarding no puede dejar a nadie sin entrar")
    @GetMapping("/estado")
    public EstadoResponse estado(@ActorAutenticado UserId actor) {
        EstadoDeMiDiagnostico e = diagnostico.estado(actor);
        return new EstadoResponse(e.ficha().name(), e.estado(), e.version(), e.completadoEn());
    }

    @RequiresPermission(value = Permission.USE_APP, scope = SOLO_LAS_PROPIAS)
    @GetMapping
    public DiagnosticoResponse abrir(@ActorAutenticado UserId actor) {
        return DiagnosticoResponse.from(diagnostico.abrir(actor));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = SOLO_LAS_PROPIAS)
    @PutMapping("/respuestas")
    public GuardadasResponse guardar(@ActorAutenticado UserId actor, @Valid @RequestBody GuardarRespuestasRequest request) {
        return new GuardadasResponse(diagnostico.guardar(actor, request.respuestas().stream().map(DiagnosticoDtos.RespuestaRequest::aEntrante).toList()));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = SOLO_LAS_PROPIAS)
    @PostMapping("/terminar")
    public TerminarResponse terminar(@ActorAutenticado UserId actor) {
        ResultadoDeTerminar r = diagnostico.terminar(actor);
        return new TerminarResponse(r.estado(), r.objetivos().stream().map(ObjetivoResponse::from).toList(), r.prioridad(), r.primeraAccion());
    }

    @RequiresPermission(Permission.USE_APP)
    @SinInterruptor(motivo = "Plan lee la prioridad y la primera acción para ordenar sus ejes: apagar el onboarding no puede dejar a Plan sin ellos")
    @GetMapping("/resumen")
    public ResumenResponse resumen(@ActorAutenticado UserId actor) {
        ResumenDeMiDiagnostico r = diagnostico.resumen(actor);
        return new ResumenResponse(r.completado(), r.prioridad(), r.primeraAccion(), r.objetivos().stream().map(ObjetivoResponse::from).toList());
    }

    @RequiresPermission(value = Permission.USE_APP, scope = SOLO_LAS_PROPIAS)
    @GetMapping("/lectura")
    public LecturaDelDiagnostico lectura(@ActorAutenticado UserId actor) {
        diagnostico.estado(actor);
        return lecturas.lecturaDe(actor);
    }

    /** Las obligatorias que faltan, con sus ids, para llevar a la persona a la primera. */
    @ExceptionHandler(DiagnosticoIncompletoException.class)
    public ResponseEntity<IncompletoResponse> incompleto(DiagnosticoIncompletoException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body(new IncompletoResponse(e.getMessage(), Instant.now(), DiagnosticoIncompletoException.CODIGO, e.faltan()));
    }

    record IncompletoResponse(String message, Instant timestamp, String codigo, List<Integer> faltan) {
    }
}
