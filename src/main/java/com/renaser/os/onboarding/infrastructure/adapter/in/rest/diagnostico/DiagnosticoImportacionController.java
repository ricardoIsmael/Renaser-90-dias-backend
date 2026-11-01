package com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico;

import com.renaser.os.onboarding.application.ports.in.diagnostico.ImportarDiagnosticoUseCase;
import com.renaser.os.onboarding.application.ports.in.diagnostico.ImportarDiagnosticoUseCase.Modo;
import com.renaser.os.onboarding.application.ports.in.diagnostico.ImportarDiagnosticoUseCase.ResultadoDeImportacion;
import com.renaser.os.onboarding.application.ports.in.diagnostico.ImportarDiagnosticoUseCase.VistaPreviaDeImportacion;
import com.renaser.os.onboarding.domain.model.diagnostico.ImportacionConErroresException;
import com.renaser.os.onboarding.domain.model.diagnostico.LecturaDeImportacion.ErrorDeImportacion;
import com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico.DiagnosticoDtos.ImportarRequest;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

/** Importar preguntas del diagnóstico desde una hoja pegada como texto (spec 026, contrato §5). */
@RestController
@RequestMapping("/api/v1/admin/diagnostico/importacion")
@Funcionalidad("admin.personas")
public class DiagnosticoImportacionController {

    private static final String SOLO_ADMIN = "solo ADMIN/ALCHEMIST activos: el servicio rechaza a los demás roles";

    private final ImportarDiagnosticoUseCase importar;

    public DiagnosticoImportacionController(ImportarDiagnosticoUseCase importar) {
        this.importar = importar;
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PostMapping("/vista-previa")
    public VistaPreviaResponse vistaPrevia(@ActorAutenticado UserId actor, @Valid @RequestBody ImportarRequest request) {
        VistaPreviaDeImportacion v = importar.revisar(actor, request.texto(), modoDe(request));
        return new VistaPreviaResponse(v.lectura().secciones().size(), v.lectura().preguntas().size(),
                v.lectura().preguntas().stream().map(p -> new FilaResponse(p.fila(), p.seccion(), p.clave(), p.pregunta().texto(),
                        com.renaser.os.onboarding.domain.model.diagnostico.PlantillaDeImportacion.nombreDelTipo(p.tipo()))).toList(),
                v.lectura().errores().stream().map(e -> new ErrorResponse(e.fila(), e.columna(), e.mensaje())).toList(),
                v.lectura().avisos().stream().map(a -> new AvisoResponse(a.fila(), a.mensaje())).toList(), v.preguntasQueSeRetirarian(), v.conRespuestas());
    }

    @RequiresPermission(value = Permission.MANAGE_TRAINEES, scope = SOLO_ADMIN)
    @PostMapping
    public ImportadaResponse importar(@ActorAutenticado UserId actor, @Valid @RequestBody ImportarRequest request) {
        ResultadoDeImportacion r = importar.importar(actor, request.texto(), modoDe(request));
        return new ImportadaResponse(r.secciones(), r.preguntas(), r.eliminadas(), r.retiradas());
    }

    @ExceptionHandler(ImportacionConErroresException.class)
    public ResponseEntity<ConErrores> conErrores(ImportacionConErroresException e) {
        List<ErrorResponse> errores = e.errores().stream().map(DiagnosticoImportacionController::aError).toList();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body(new ConErrores(e.getMessage(), Instant.now(), ImportacionConErroresException.CODIGO, errores));
    }

    private static Modo modoDe(ImportarRequest request) {
        if (request.modo() == null || request.modo().isBlank()) {
            return Modo.ANADIR;
        }
        try {
            return Modo.valueOf(request.modo().trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El modo de importación es ANADIR o REEMPLAZAR");
        }
    }

    private static ErrorResponse aError(ErrorDeImportacion e) {
        return new ErrorResponse(e.fila(), e.columna(), e.mensaje());
    }

    record FilaResponse(int numero, String seccion, String clave, String texto, String tipo) {
    }

    record ErrorResponse(int fila, String columna, String mensaje) {
    }

    record AvisoResponse(int fila, String mensaje) {
    }

    record VistaPreviaResponse(int secciones, int preguntas, List<FilaResponse> filas, List<ErrorResponse> errores, List<AvisoResponse> advertencias,
                               int preguntasQueSeRetirarian, int conRespuestas) {
    }

    record ImportadaResponse(int secciones, int preguntas, int eliminadas, int retiradas) {
    }

    record ConErrores(String message, Instant timestamp, String codigo, List<ErrorResponse> errores) {
    }
}
