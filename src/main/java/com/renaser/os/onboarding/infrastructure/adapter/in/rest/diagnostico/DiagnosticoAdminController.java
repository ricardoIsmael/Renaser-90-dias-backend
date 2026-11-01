package com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico;

import com.renaser.os.onboarding.application.ports.in.diagnostico.AsignarFichaUseCase;
import com.renaser.os.onboarding.application.ports.in.diagnostico.AsignarFichaUseCase.ResultadoDeAsignacion;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase.DiagnosticoAbierto;
import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase;
import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase.ResultadoDeQuitar;
import com.renaser.os.onboarding.application.ports.in.diagnostico.PublicarDiagnosticoUseCase;
import com.renaser.os.onboarding.domain.model.diagnostico.NoSePuedePublicarException;
import com.renaser.os.onboarding.domain.model.diagnostico.OtraPersonaPublicoException;
import com.renaser.os.onboarding.domain.model.diagnostico.TieneDependientesException;
import com.renaser.os.onboarding.domain.model.estado.TipoDeFicha;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.AsignadasResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.AsignarFichaRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.BorradorResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.DiagnosticoResponse;
import com.renaser.os.onboarding.api.DiagnosticoDeLaPersonaFinder.LecturaDelDiagnostico;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.MoverRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.OmitidaResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.OrdenRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.PreguntaAdminResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.PreguntaRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.PublicarRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.QuitarRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.QuitarResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.SeccionAdminResponse;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.SeccionRequest;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.VersionResponse;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Administración de las fichas (spec 026, contrato §5): el borrador, la vista previa, publicar y la ficha de cada persona. Solo Administración y Alquimista. */
@RestController
@RequestMapping("/api/v1/admin/diagnostico")
@Funcionalidad("admin.personas")
public class DiagnosticoAdminController {

    private static final String SOLO_ADMIN = "solo ADMIN/ALCHEMIST activos: el servicio rechaza a los demás roles";

    private final EditarDiagnosticoUseCase editar;
    private final PublicarDiagnosticoUseCase publicar;
    private final AsignarFichaUseCase asignar;

    public DiagnosticoAdminController(EditarDiagnosticoUseCase editar, PublicarDiagnosticoUseCase publicar, AsignarFichaUseCase asignar) {
        this.editar = editar;
        this.publicar = publicar;
        this.asignar = asignar;
    }

    // --------------------------------------------------------------------------------------------- borrador

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @GetMapping
    public BorradorResponse ver(@ActorAutenticado UserId actor) {
        return BorradorResponse.from(publicar.ver(actor));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @GetMapping("/vista-previa")
    public DiagnosticoResponse vistaPrevia(@ActorAutenticado UserId actor, @RequestParam(required = false) String formacion,
                                           @RequestParam(required = false) Integer duracionDias) {
        Formacion f = formacion == null || formacion.isBlank() ? null : Formacion.desde(formacion);
        DiagnosticoAbierto d = publicar.vistaPrevia(actor, f, duracionDias);
        return DiagnosticoResponse.from(d);
    }

    // -------------------------------------------------------------------------------------------- secciones

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PostMapping("/secciones")
    public SeccionAdminResponse crearSeccion(@ActorAutenticado UserId actor, @Valid @RequestBody SeccionRequest request) {
        return SeccionAdminResponse.from(editar.crearSeccion(actor, request.titulo(), request.descripcion()));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PutMapping("/secciones/{id}")
    public SeccionAdminResponse actualizarSeccion(@ActorAutenticado UserId actor, @PathVariable int id, @Valid @RequestBody SeccionRequest request) {
        return SeccionAdminResponse.from(editar.actualizarSeccion(actor, id, request.titulo(), request.descripcion()));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @DeleteMapping("/secciones/{id}")
    public QuitarResponse eliminarSeccion(@ActorAutenticado UserId actor, @PathVariable int id,
                                          @RequestParam(defaultValue = "false") boolean quitarDependientes) {
        return aRespuesta(editar.eliminarSeccion(actor, id, quitarDependientes));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PutMapping("/secciones/orden")
    public ResponseEntity<Void> ordenarSecciones(@ActorAutenticado UserId actor, @Valid @RequestBody OrdenRequest request) {
        editar.ordenarSecciones(actor, request.ids());
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------------------------- preguntas

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PostMapping("/preguntas")
    public PreguntaAdminResponse crearPregunta(@ActorAutenticado UserId actor, @Valid @RequestBody PreguntaRequest request) {
        return PreguntaAdminResponse.from(editar.crearPregunta(actor, request.aPedida()));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PutMapping("/preguntas/{id}")
    public PreguntaAdminResponse actualizarPregunta(@ActorAutenticado UserId actor, @PathVariable int id, @Valid @RequestBody PreguntaRequest request) {
        return PreguntaAdminResponse.from(editar.actualizarPregunta(actor, id, request.aPedida()));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PutMapping("/preguntas/{id}/mover")
    public ResponseEntity<Void> mover(@ActorAutenticado UserId actor, @PathVariable int id, @Valid @RequestBody MoverRequest request) {
        editar.moverPregunta(actor, id, request.seccionId(), request.posicion());
        return ResponseEntity.noContent().build();
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PostMapping("/preguntas/{id}/duplicar")
    public PreguntaAdminResponse duplicar(@ActorAutenticado UserId actor, @PathVariable int id) {
        return PreguntaAdminResponse.from(editar.duplicarPregunta(actor, id));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PostMapping("/preguntas/quitar")
    public QuitarResponse quitar(@ActorAutenticado UserId actor, @Valid @RequestBody QuitarRequest request) {
        return aRespuesta(editar.quitar(actor, request.ids(), request.conDependientes()));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PostMapping("/preguntas/{id}/restaurar")
    public PreguntaAdminResponse restaurar(@ActorAutenticado UserId actor, @PathVariable int id) {
        return PreguntaAdminResponse.from(editar.restaurar(actor, id));
    }

    // -------------------------------------------------------------------------------- publicar y versiones

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PostMapping("/publicar")
    public VersionResponse publicar(@ActorAutenticado UserId actor, @Valid @RequestBody PublicarRequest request) {
        return VersionResponse.from(publicar.publicar(actor, request.versionBase()));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @GetMapping("/versiones")
    public List<VersionResponse> versiones(@ActorAutenticado UserId actor) {
        return publicar.versiones(actor).stream().map(VersionResponse::from).toList();
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @GetMapping(value = "/plantilla.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> plantilla(@ActorAutenticado UserId actor) {
        return comoCsv(publicar.plantilla(actor), "plantilla-del-diagnostico.csv");
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @GetMapping(value = "/exportar.csv", produces = "text/csv;charset=UTF-8")
    public ResponseEntity<byte[]> exportar(@ActorAutenticado UserId actor) {
        return comoCsv(publicar.exportar(actor), "diagnostico.csv");
    }

    // ----------------------------------------------------------------------------------------- las personas

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @GetMapping("/personas/{id}")
    public LecturaDelDiagnostico lecturaDe(@ActorAutenticado UserId actor, @PathVariable UUID id) {
        return publicar.lecturaDe(actor, UserId.of(id));
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PostMapping("/personas/ficha")
    public AsignadasResponse asignarFicha(@ActorAutenticado UserId actor, @Valid @RequestBody AsignarFichaRequest request) {
        TipoDeFicha tipo = TipoDeFicha.valueOf(request.ficha().trim().toUpperCase());
        List<UserId> personas = request.ids().stream().map(id -> UserId.of(UUID.fromString(id))).toList();
        ResultadoDeAsignacion r = asignar.asignar(actor, personas, tipo);
        return new AsignadasResponse(r.asignadas(),
                r.omitidas().stream().map(o -> new OmitidaResponse(o.id().toString(), o.nombre(), o.motivo())).toList());
    }

    // ------------------------------------------------------------------------------------------------ errores

    @ExceptionHandler(TieneDependientesException.class)
    public ResponseEntity<ConDependientes> tieneDependientes(TieneDependientesException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body(new ConDependientes(e.getMessage(), Instant.now(), TieneDependientesException.CODIGO, e.dependientes()));
    }

    @ExceptionHandler(OtraPersonaPublicoException.class)
    public ResponseEntity<ConVersion> otraPersonaPublico(OtraPersonaPublicoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body(new ConVersion(e.getMessage(), Instant.now(), OtraPersonaPublicoException.CODIGO, e.versionVigente()));
    }

    @ExceptionHandler(NoSePuedePublicarException.class)
    public ResponseEntity<ConMotivos> noSePuedePublicar(NoSePuedePublicarException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body(new ConMotivos(e.getMessage(), Instant.now(), NoSePuedePublicarException.CODIGO, e.motivos()));
    }

    private static QuitarResponse aRespuesta(ResultadoDeQuitar r) {
        return new QuitarResponse(r.eliminadas(), r.retiradas(), r.conRespuestas());
    }

    private static ResponseEntity<byte[]> comoCsv(String contenido, String nombre) {
        byte[] cuerpo = ("﻿" + contenido).getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + nombre + "\"").body(cuerpo);
    }

    record ConDependientes(String message, Instant timestamp, String codigo, List<Integer> dependientes) {
    }

    record ConVersion(String message, Instant timestamp, String codigo, int versionVigente) {
    }

    record ConMotivos(String message, Instant timestamp, String codigo, List<String> motivos) {
    }
}
