package com.renaser.os.onboarding.infrastructure.adapter.in.rest.mapa;

import com.renaser.os.onboarding.application.ports.in.mapa.CompletarEtapaMapaUseCase;
import com.renaser.os.onboarding.application.ports.in.mapa.ConsultarMapaUseCase;
import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase;
import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase.AccionEntrada;
import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase.GuardarAccionesCommand;
import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase.GuardarProtocolosCommand;
import com.renaser.os.onboarding.application.ports.in.mapa.GuardarPasoMapaUseCase.ProtocoloEntrada;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Mapa de Renacimiento (Dia 7) — las dos listas que el motor de cuestionarios no puede representar.
 *
 * <p><b>PUT y no POST, y por vista entera.</b> Guardar por PASO fue decision del dueno del proyecto
 * (2026-09-08): el cliente manda V06 o V07 completa y el servidor reemplaza lo que hubiera. Es
 * idempotente por definicion —repetir el mismo PUT deja el mismo estado— asi que un reintento tras
 * un timeout de red no puede duplicar nada.
 */
@RestController
@RequestMapping("/api/v1/mapa-renacimiento")
public class MapaRenacimientoController {

    private final GuardarPasoMapaUseCase guardarPasoMapaUseCase;
    private final ConsultarMapaUseCase consultarMapaUseCase;
    private final CompletarEtapaMapaUseCase completarEtapaMapaUseCase;

    public MapaRenacimientoController(GuardarPasoMapaUseCase guardarPasoMapaUseCase,
                                       ConsultarMapaUseCase consultarMapaUseCase,
                                       CompletarEtapaMapaUseCase completarEtapaMapaUseCase) {
        this.guardarPasoMapaUseCase = guardarPasoMapaUseCase;
        this.consultarMapaUseCase = consultarMapaUseCase;
        this.completarEtapaMapaUseCase = completarEtapaMapaUseCase;
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public MapaResponse consultar(@ActorAutenticado UserId actor) {
        return MapaResponse.from(consultarMapaUseCase.consultar(actor));
    }

    @RequiresPermission(Permission.USE_APP)
    @PutMapping("/acciones")
    public ResponseEntity<Void> guardarAcciones(@ActorAutenticado UserId actor,
                                                 @Valid @RequestBody GuardarAccionesMapaRequest request) {
        guardarPasoMapaUseCase.guardarSistemaDeEjecucion(new GuardarAccionesCommand(actor, aEntradas(request)));
        return ResponseEntity.noContent().build();
    }

    @RequiresPermission(Permission.USE_APP)
    @PutMapping("/reemplazos")
    public ResponseEntity<Void> guardarProtocolos(@ActorAutenticado UserId actor,
                                                   @Valid @RequestBody GuardarProtocolosMapaRequest request) {
        guardarPasoMapaUseCase.guardarProtocolosDeReemplazo(
                new GuardarProtocolosCommand(actor, aEntradasProtocolo(request)));
        return ResponseEntity.noContent().build();
    }

    /** Marca la etapa como terminada. NO activa el mapa: activar crea habitos y es la fase 4. */
    @RequiresPermission(Permission.USE_APP)
    @PostMapping("/completar")
    public ResponseEntity<Void> completar(@ActorAutenticado UserId actor) {
        completarEtapaMapaUseCase.completar(actor);
        return ResponseEntity.noContent().build();
    }

    private static List<AccionEntrada> aEntradas(GuardarAccionesMapaRequest request) {
        return request.actions().stream()
                .map(a -> new AccionEntrada(a.actionId(), a.area(), a.text(), a.weeklyFrequency(),
                        a.days() == null ? List.of() : a.days(), a.moment(), a.evidence()))
                .toList();
    }

    private static List<ProtocoloEntrada> aEntradasProtocolo(GuardarProtocolosMapaRequest request) {
        return request.protocols().stream()
                .map(p -> new ProtocoloEntrada(p.protocolId(), p.pattern(), p.trigger(), p.currentBehavior(),
                        p.alternativeResponse()))
                .toList();
    }
}
