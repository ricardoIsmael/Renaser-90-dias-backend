package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocadiaria;

import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase.CompletarRocaDiariaCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeHoyUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.ConsultarRocasDeMananaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.CrearPlanDiarioCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.ItemRocaDiaria;
import com.renaser.os.rocks.application.ports.in.rocadiaria.SolicitarUrlAdjuntoRocaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.SolicitarUrlAdjuntoRocaUseCase.SolicitarUrlAdjuntoRocaCommand;
import com.renaser.os.rocks.domain.model.rocadiaria.TipoEvidenciaRoca;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/rocks")
public class RocaDiariaController {

    private final CrearPlanDiarioUseCase crearUseCase;
    private final CompletarRocaDiariaUseCase completarUseCase;
    private final SolicitarUrlAdjuntoRocaUseCase urlAdjuntoUseCase;
    private final ConsultarRocasDeHoyUseCase hoyUseCase;
    private final ConsultarRocasDeMananaUseCase mananaUseCase;

    public RocaDiariaController(CrearPlanDiarioUseCase crearUseCase, CompletarRocaDiariaUseCase completarUseCase,
                                 SolicitarUrlAdjuntoRocaUseCase urlAdjuntoUseCase,
                                 ConsultarRocasDeHoyUseCase hoyUseCase, ConsultarRocasDeMananaUseCase mananaUseCase) {
        this.crearUseCase = crearUseCase;
        this.completarUseCase = completarUseCase;
        this.urlAdjuntoUseCase = urlAdjuntoUseCase;
        this.hoyUseCase = hoyUseCase;
        this.mananaUseCase = mananaUseCase;
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping("/today")
    public List<RocaDiariaResponse> hoy(@ActorAutenticado UserId actor) {
        return hoyUseCase.hoy(actor).stream().map(RocaDiariaResponse::from).toList();
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping("/tomorrow")
    public List<RocaDiariaResponse> manana(@ActorAutenticado UserId actor) {
        return mananaUseCase.manana(actor).stream().map(RocaDiariaResponse::from).toList();
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "exige tener las rocas maestras completas (ROCKS_LOCKED)")
    @PostMapping("/plan")
    public ResponseEntity<List<RocaDiariaResponse>> crear(@ActorAutenticado UserId actor,
                                                            @Valid @RequestBody CrearPlanDiarioRequest request) {
        List<ItemRocaDiaria> items = request.rocas().stream()
                .map(item -> new ItemRocaDiaria(EjeObjetivo.valueOf(item.eje()), item.posicion(), item.titulo(),
                        item.descripcion(), item.puntajeImpacto(), item.esDelegable(), item.horaInicio(),
                        item.horaFin()))
                .toList();
        var creadas = crearUseCase.crear(new CrearPlanDiarioCommand(actor, request.fecha(), items));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(creadas.stream().map(RocaDiariaResponse::from).toList());
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "dueno de la roca diaria")
    @PostMapping("/{id}/evidence/upload-url")
    public UrlAdjuntoResponse urlDeSubida(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                           @Valid @RequestBody SolicitarUrlAdjuntoRequest request) {
        var url = urlAdjuntoUseCase.solicitarUrl(new SolicitarUrlAdjuntoRocaCommand(actor,
                RocaDiariaId.of(id), request.tipoContenido()));
        return UrlAdjuntoResponse.from(url);
    }

    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "dueno de la roca diaria, y la roca VERDE del eje primero (Pareto)")
    @PostMapping("/{id}/evidence")
    public RocaDiariaResponse completar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                         @Valid @RequestBody CompletarRocaDiariaRequest request) {
        boolean esPrincipal = request.esPrincipal() == null || request.esPrincipal();
        boolean publishedToWall = Boolean.TRUE.equals(request.publishedToWall());
        var completada = completarUseCase.completar(new CompletarRocaDiariaCommand(actor,
                RocaDiariaId.of(id), TipoEvidenciaRoca.valueOf(request.tipo()), request.bucket(),
                request.rutaStorage(), request.contenidoTexto(), request.timestampExif(), request.gpsLat(),
                request.gpsLng(), esPrincipal, publishedToWall));
        return RocaDiariaResponse.from(completada);
    }
}
