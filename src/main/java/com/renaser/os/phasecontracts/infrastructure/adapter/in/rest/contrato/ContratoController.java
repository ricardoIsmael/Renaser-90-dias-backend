package com.renaser.os.phasecontracts.infrastructure.adapter.in.rest.contrato;

import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosPendientesUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ConsultarContratosUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.FirmarContratoUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.FirmarContratoUseCase.FirmarContratoCommand;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ObtenerUrlFirmaContratoUseCase;
import com.renaser.os.phasecontracts.application.ports.in.contrato.ObtenerUrlFirmaContratoUseCase.ObtenerUrlFirmaContratoCommand;
import com.renaser.os.shared.domain.UserId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
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

    @GetMapping
    public List<ContratoFaseResponse> listar(@RequestHeader("X-Actor-Id") String actorId) {
        return consultarUseCase.consultarDeParticipante(UserId.of(actorId)).stream()
                .map(ContratoFaseResponse::deListado)
                .toList();
    }

    @GetMapping("/pending")
    public ContratoPendienteResponse pendiente(@RequestHeader("X-Actor-Id") String actorId) {
        return ContratoPendienteResponse.from(pendientesUseCase.consultarPendiente(UserId.of(actorId)));
    }

    @PostMapping("/upload-url")
    public ResponseEntity<UrlFirmaResponse> urlDeSubida(@RequestHeader("X-Actor-Id") String actorId) {
        var url = urlFirmaUseCase.obtenerUrlSubida(new ObtenerUrlFirmaContratoCommand(UserId.of(actorId)));
        return ResponseEntity.ok(UrlFirmaResponse.from(url));
    }

    @PostMapping
    public ResponseEntity<ContratoFaseResponse> firmar(@RequestHeader("X-Actor-Id") String actorId) {
        var contrato = firmarUseCase.firmar(new FirmarContratoCommand(UserId.of(actorId)));
        return ResponseEntity.status(HttpStatus.CREATED).body(ContratoFaseResponse.deFirma(contrato));
    }
}
