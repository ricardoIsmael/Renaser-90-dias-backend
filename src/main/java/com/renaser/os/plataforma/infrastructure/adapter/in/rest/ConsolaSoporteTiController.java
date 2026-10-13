package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.ConsultarAuditoriaUseCase;
import com.renaser.os.plataforma.application.ports.in.ConsultarCircuitoIaUseCase;
import com.renaser.os.plataforma.application.ports.in.DesbloquearConsolaUseCase;
import com.renaser.os.plataforma.application.ports.in.DesbloquearConsolaUseCase.DesbloquearConsolaCommand;
import com.renaser.os.plataforma.application.ports.in.DesbloquearConsolaUseCase.Desbloqueo;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * La consola Soporte TI (contrato §6): desbloqueo, auditoría y circuito de la IA. Los interruptores están en
 * {@link FuncionalidadesSoporteTiController}.
 *
 * <p>Solo IT_SUPPORT ({@code OPERATE_PLATFORM}, estricto en el interceptor y repetido en cada servicio). Todo menos el
 * desbloqueo exige {@code X-Consola-Token} vivo (423 si falta o venció).
 */
@RestController
@RequestMapping("/api/v1/soporte-ti")
@RequiresPermission(Permission.OPERATE_PLATFORM)
@SinInterruptor(motivo = "La consola Soporte TI no se puede apagar: es desde donde se prende todo lo demás "
        + "(anti-bloqueo, FR-004)")
class ConsolaSoporteTiController {

    private final DesbloquearConsolaUseCase desbloquear;
    private final ConsultarAuditoriaUseCase auditoria;
    private final ConsultarCircuitoIaUseCase circuito;

    ConsolaSoporteTiController(DesbloquearConsolaUseCase desbloquear, ConsultarAuditoriaUseCase auditoria,
                               ConsultarCircuitoIaUseCase circuito) {
        this.desbloquear = desbloquear;
        this.auditoria = auditoria;
        this.circuito = circuito;
    }

    @PostMapping("/desbloqueo")
    public DesbloqueoResponse desbloquear(@ActorAutenticado UserId actor, @RequestBody @Valid DesbloqueoRequest cuerpo,
                                          HttpServletRequest request) {
        Desbloqueo desbloqueo = desbloquear.desbloquear(new DesbloquearConsolaCommand(
                PedidoDeConsola.acceso(actor, request), cuerpo.contrasena(), cuerpo.para(),
                PedidoDeConsola.dispositivo(request)));
        return new DesbloqueoResponse(desbloqueo.token(), desbloqueo.venceEn());
    }

    @GetMapping("/auditoria")
    public AuditoriaResponse auditoria(@ActorAutenticado UserId actor,
                                       @RequestParam(required = false) String clave,
                                       @RequestParam(name = "actor", required = false) UUID quien,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant desde,
                                       @RequestParam(required = false)
                                       @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant hasta,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size,
                                       HttpServletRequest request) {
        return AuditoriaResponse.from(auditoria.consultar(PedidoDeConsola.acceso(actor, request),
                new ConsultarAuditoriaUseCase.Filtro(clave == null || clave.isBlank() ? null : clave,
                        quien == null ? null : UserId.of(quien), desde, hasta, page, size)));
    }

    @GetMapping("/ia/circuito")
    public List<CircuitoResponse> circuito(@ActorAutenticado UserId actor, HttpServletRequest request) {
        return circuito.circuitos(PedidoDeConsola.acceso(actor, request)).stream().map(CircuitoResponse::from)
                .toList();
    }
}
