package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal;

import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase.CerrarSemanaCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.CrearPlanSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.ItemRocaSemanal;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase.EditarRocaSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ObjetivosSinRedUseCase;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rocks/weekly")
@Funcionalidad("plan")
public class RocaSemanalController {

    private final CrearPlanSemanalUseCase crearUseCase;
    private final EditarDentroDe48hUseCase editarUseCase;
    private final CerrarSemanaUseCase cerrarUseCase;
    private final ConsultarRocasSemanalesUseCase consultarUseCase;
    private final ObjetivosSinRedUseCase sinRedUseCase;

    public RocaSemanalController(CrearPlanSemanalUseCase crearUseCase, EditarDentroDe48hUseCase editarUseCase,
                                  CerrarSemanaUseCase cerrarUseCase, ConsultarRocasSemanalesUseCase consultarUseCase,
                                  ObjetivosSinRedUseCase sinRedUseCase) {
        this.sinRedUseCase = sinRedUseCase;
        this.crearUseCase = crearUseCase;
        this.editarUseCase = editarUseCase;
        this.cerrarUseCase = cerrarUseCase;
        this.consultarUseCase = consultarUseCase;
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping
    public List<RocaSemanalResponse> listar(@ActorAutenticado UserId actor,
                                             @RequestParam(required = false) Integer semana) {
        return consultarUseCase.misRocasSemanales(actor, semana).stream()
                .map(RocaSemanalResponse::from)
                .toList();
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "exige tener las rocas maestras completas (ROCKS_LOCKED)")
    @PostMapping
    public ResponseEntity<List<RocaSemanalResponse>> crear(@ActorAutenticado UserId actor, HechoSinRed sinRed,
                                                             @Valid @RequestBody CrearPlanSemanalRequest request) {
        List<ItemRocaSemanal> items = request.rocas().stream()
                .map(item -> new ItemRocaSemanal(EjeObjetivo.valueOf(item.eje()), item.titulo(),
                        item.obstaculo(), item.contingencia(), item.autoevaluacionInicio()))
                .toList();
        var comando = new CrearPlanSemanalCommand(actor, items);
        // Spec 048 (FR-019): con X-Id-Cliente y X-Hecho-En es lo hecho sin red, con su hora real; sin ellas, lo de siempre.
        var creadas = sinRed == null ? crearUseCase.crear(comando) : sinRedUseCase.crearSemana(comando, sinRed);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creadas.stream().map(RocaSemanalResponse::from).toList());
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "dueno de la roca semanal, y dentro de la ventana de edicion")
    @PatchMapping("/{id}")
    public RocaSemanalResponse editar(@ActorAutenticado UserId actor, @PathVariable UUID id, HechoSinRed sinRed,
                                       @Valid @RequestBody EditarRocaSemanalRequest request) {
        var comando = new EditarRocaSemanalCommand(actor, RocaSemanalId.of(id),
                request.titulo(), request.obstaculo(), request.contingencia(),
                request.autoevaluacionInicio());
        var editada = sinRed == null ? editarUseCase.editar(comando) : sinRedUseCase.editarSemanal(comando, sinRed);
        return RocaSemanalResponse.from(editada);
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "dueno de la roca semanal")
    @PatchMapping("/{id}/review")
    public RocaSemanalResponse cerrar(@ActorAutenticado UserId actor, @PathVariable UUID id, HechoSinRed sinRed,
                                       @Valid @RequestBody CerrarSemanaRequest request) {
        var comando = new CerrarSemanaCommand(actor, RocaSemanalId.of(id),
                request.autoevaluacionFin(), request.bloqueoPrincipal(), request.correccion());
        var cerrada = sinRed == null ? cerrarUseCase.cerrar(comando) : sinRedUseCase.cerrarSemana(comando, sinRed);
        return RocaSemanalResponse.from(cerrada);
    }
}
