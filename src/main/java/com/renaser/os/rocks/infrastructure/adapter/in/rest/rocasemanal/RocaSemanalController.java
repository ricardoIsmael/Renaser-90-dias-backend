package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal;

import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase.CerrarSemanaCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ConsultarRocasSemanalesUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.CrearPlanSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.ItemRocaSemanal;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase.EditarRocaSemanalCommand;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
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
public class RocaSemanalController {

    private final CrearPlanSemanalUseCase crearUseCase;
    private final EditarDentroDe48hUseCase editarUseCase;
    private final CerrarSemanaUseCase cerrarUseCase;
    private final ConsultarRocasSemanalesUseCase consultarUseCase;

    public RocaSemanalController(CrearPlanSemanalUseCase crearUseCase, EditarDentroDe48hUseCase editarUseCase,
                                  CerrarSemanaUseCase cerrarUseCase, ConsultarRocasSemanalesUseCase consultarUseCase) {
        this.crearUseCase = crearUseCase;
        this.editarUseCase = editarUseCase;
        this.cerrarUseCase = cerrarUseCase;
        this.consultarUseCase = consultarUseCase;
    }

    @GetMapping
    public List<RocaSemanalResponse> listar(@ActorAutenticado UserId actor,
                                             @RequestParam(required = false) Integer semana) {
        return consultarUseCase.misRocasSemanales(actor, semana).stream()
                .map(RocaSemanalResponse::from)
                .toList();
    }

    @PostMapping
    public ResponseEntity<List<RocaSemanalResponse>> crear(@ActorAutenticado UserId actor,
                                                             @Valid @RequestBody CrearPlanSemanalRequest request) {
        List<ItemRocaSemanal> items = request.rocas().stream()
                .map(item -> new ItemRocaSemanal(EjeObjetivo.valueOf(item.eje()), item.titulo(),
                        item.accionCritica1(), item.accionCritica2(), item.accionCritica3(), item.obstaculo(),
                        item.contingencia(), item.autoevaluacionInicio()))
                .toList();
        var creadas = crearUseCase.crear(new CrearPlanSemanalCommand(actor, items));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creadas.stream().map(RocaSemanalResponse::from).toList());
    }

    @PatchMapping("/{id}")
    public RocaSemanalResponse editar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                       @Valid @RequestBody EditarRocaSemanalRequest request) {
        var editada = editarUseCase.editar(new EditarRocaSemanalCommand(actor, RocaSemanalId.of(id),
                request.titulo(), request.accionesCriticas(), request.obstaculo(), request.contingencia(),
                request.autoevaluacionInicio()));
        return RocaSemanalResponse.from(editada);
    }

    @PatchMapping("/{id}/review")
    public RocaSemanalResponse cerrar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                       @Valid @RequestBody CerrarSemanaRequest request) {
        var cerrada = cerrarUseCase.cerrar(new CerrarSemanaCommand(actor, RocaSemanalId.of(id),
                request.autoevaluacionFin(), request.bloqueoPrincipal(), request.correccion()));
        return RocaSemanalResponse.from(cerrada);
    }
}
