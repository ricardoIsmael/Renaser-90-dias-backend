package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.BuscarPersonasUseCase;
import com.renaser.os.plataforma.application.ports.in.CambiarDespliegueUseCase;
import com.renaser.os.plataforma.application.ports.in.CambiarDespliegueUseCase.CambiarDespliegueCommand;
import com.renaser.os.plataforma.application.ports.in.CambiarProbadorasUseCase;
import com.renaser.os.plataforma.application.ports.in.CambiarProbadorasUseCase.ProbadoraCommand;
import com.renaser.os.plataforma.application.ports.in.ConsultarDespliegueUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * El despliegue progresivo en la consola Soporte TI (contrato fase 2 §3.4): ver y mover el escalón, sumar y quitar
 * probadores, y buscar a quién sumar.
 *
 * <p>Misma autorización que la consola de la fase 1: solo IT_SUPPORT ({@code OPERATE_PLATFORM}, estricto en el
 * interceptor y repetido en cada servicio) y {@code X-Consola-Token} vivo (423). Un paso crítico pide además
 * {@code X-Reautenticacion} (428).
 */
@RestController
@RequestMapping("/api/v1/soporte-ti")
@RequiresPermission(Permission.OPERATE_PLATFORM)
@SinInterruptor(motivo = "La consola Soporte TI no se puede apagar: es desde donde se prende todo lo demás "
        + "(anti-bloqueo, FR-004)")
class DespliegueSoporteTiController {

    private final ConsultarDespliegueUseCase consultar;
    private final CambiarDespliegueUseCase cambiar;
    private final CambiarProbadorasUseCase probadoras;
    private final BuscarPersonasUseCase personas;

    DespliegueSoporteTiController(ConsultarDespliegueUseCase consultar, CambiarDespliegueUseCase cambiar,
                                  CambiarProbadorasUseCase probadoras, BuscarPersonasUseCase personas) {
        this.consultar = consultar;
        this.cambiar = cambiar;
        this.probadoras = probadoras;
        this.personas = personas;
    }

    @GetMapping("/funcionalidades/{clave}/despliegue")
    public DespliegueResponse despliegue(@ActorAutenticado UserId actor, @PathVariable String clave,
                                         HttpServletRequest request) {
        return DespliegueResponse.from(consultar.detalle(PedidoDeConsola.acceso(actor, request), clave));
    }

    @PutMapping("/funcionalidades/{clave}/despliegue")
    public DespliegueResponse cambiar(@ActorAutenticado UserId actor, @PathVariable String clave,
                                      @RequestBody @Valid CambioDeDespliegueRequest cuerpo,
                                      HttpServletRequest request) {
        return DespliegueResponse.from(cambiar.cambiar(new CambiarDespliegueCommand(
                PedidoDeConsola.acceso(actor, request), clave, cuerpo.porcentaje(), cuerpo.motivo(), cuerpo.version(),
                request.getHeader(PedidoDeConsola.REAUTENTICACION), PedidoDeConsola.dispositivo(request))));
    }

    @PutMapping("/funcionalidades/{clave}/despliegue/probadores/{usuarioId}")
    public DespliegueResponse sumarProbadora(@ActorAutenticado UserId actor, @PathVariable String clave,
                                             @PathVariable UUID usuarioId,
                                             @RequestBody(required = false) @Valid ProbadoraRequest cuerpo,
                                             HttpServletRequest request) {
        return DespliegueResponse.from(probadoras.sumar(new ProbadoraCommand(PedidoDeConsola.acceso(actor, request),
                clave, UserId.of(usuarioId), cuerpo == null ? null : cuerpo.motivo(),
                PedidoDeConsola.dispositivo(request))));
    }

    @DeleteMapping("/funcionalidades/{clave}/despliegue/probadores/{usuarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void quitarProbadora(@ActorAutenticado UserId actor, @PathVariable String clave,
                                @PathVariable UUID usuarioId, HttpServletRequest request) {
        probadoras.quitar(new ProbadoraCommand(PedidoDeConsola.acceso(actor, request), clave, UserId.of(usuarioId),
                null, PedidoDeConsola.dispositivo(request)));
    }

    @GetMapping("/personas")
    public List<PersonaResponse> personas(@ActorAutenticado UserId actor,
                                          @RequestParam(name = "q", required = false) String texto,
                                          HttpServletRequest request) {
        return personas.buscar(PedidoDeConsola.acceso(actor, request), texto).stream().map(PersonaResponse::from)
                .toList();
    }
}
