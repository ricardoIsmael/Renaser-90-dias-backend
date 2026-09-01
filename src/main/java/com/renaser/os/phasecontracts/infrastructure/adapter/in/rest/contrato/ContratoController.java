package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.contrato;

import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.FirmarContratoUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.FirmarContratoUseCase.FirmarContratoCommand;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ObtenerUrlFirmaContratoUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ObtenerUrlFirmaContratoUseCase.ObtenerUrlFirmaContratoCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/phase-contracts")
public class ContratoController {

    private final ConsultarContratosPendientesUseCase pendientesUseCase;
    private final ConsultarContratosUseCase consultarUseCase;
    private final FirmarContratoUseCase firmarUseCase;
    private final ObtenerUrlFirmaContratoUseCase urlFirmaUseCase;

    public ContratoController(ConsultarContratosPendientesUseCase pendientesUseCase,
                               ConsultarContratosUseCase consultarUseCase, FirmarContratoUseCase firmarUseCase,
                               ObtenerUrlFirmaContratoUseCase urlFirmaUseCase) {
        this.pendientesUseCase = pendientesUseCase;
        this.consultarUseCase = consultarUseCase;
        this.firmarUseCase = firmarUseCase;
        this.urlFirmaUseCase = urlFirmaUseCase;
    }

    @RequiresPermission(Permission.VIEW_OWN_PHASE_CONTRACTS)
    @GetMapping
    public List<ContratoFaseResponse> listar(@ActorAutenticado UserId actor) {
        return consultarUseCase.consultarDeParticipante(actor).stream()
                .map(ContratoFaseResponse::deListado)
                .toList();
    }

    @RequiresPermission(Permission.VIEW_OWN_PHASE_CONTRACTS)
    @GetMapping("/pending")
    public ContratoPendienteResponse pendiente(@ActorAutenticado UserId actor) {
        return ContratoPendienteResponse.from(pendientesUseCase.consultarPendiente(actor));
    }

    @RequiresPermission(Permission.SIGN_PHASE_CONTRACT)
    @PostMapping("/upload-url")
    public ResponseEntity<UrlFirmaResponse> urlDeSubida(@ActorAutenticado UserId actor) {
        var url = urlFirmaUseCase.obtenerUrlSubida(new ObtenerUrlFirmaContratoCommand(actor));
        return ResponseEntity.ok(UrlFirmaResponse.from(url));
    }

    @RequiresPermission(Permission.SIGN_PHASE_CONTRACT)
    @PostMapping
    public ResponseEntity<ContratoFaseResponse> firmar(@ActorAutenticado UserId actor) {
        var contrato = firmarUseCase.firmar(new FirmarContratoCommand(actor));
        return ResponseEntity.status(HttpStatus.CREATED).body(ContratoFaseResponse.deFirma(contrato));
    }
}
