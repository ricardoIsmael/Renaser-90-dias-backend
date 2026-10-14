package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.CambiarFuncionalidadUseCase;
import com.renaser.os.plataforma.application.ports.in.CambiarFuncionalidadUseCase.CambiarFuncionalidadCommand;
import com.renaser.os.plataforma.application.ports.in.ConsultarFuncionalidadesUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Los interruptores de la consola Soporte TI (contrato §6): el catálogo con su estado y el cambio de uno. Un cambio
 * crítico exige además {@code X-Reautenticacion} (428 sin ella).
 */
@RestController
@RequestMapping("/api/v1/soporte-ti/funcionalidades")
@RequiresPermission(Permission.OPERATE_PLATFORM)
@SinInterruptor(motivo = "La consola Soporte TI no se puede apagar: es desde donde se prende todo lo demás "
        + "(anti-bloqueo, FR-004)")
class FuncionalidadesSoporteTiController {

    private final ConsultarFuncionalidadesUseCase consultar;
    private final CambiarFuncionalidadUseCase cambiar;

    FuncionalidadesSoporteTiController(ConsultarFuncionalidadesUseCase consultar, CambiarFuncionalidadUseCase cambiar) {
        this.consultar = consultar;
        this.cambiar = cambiar;
    }

    @GetMapping
    public List<FuncionalidadEnConsolaResponse> listar(@ActorAutenticado UserId actor, HttpServletRequest request) {
        return consultar.listar(PedidoDeConsola.acceso(actor, request)).stream()
                .map(FuncionalidadEnConsolaResponse::from).toList();
    }

    @PutMapping("/{clave}")
    public FuncionalidadEnConsolaResponse cambiar(@ActorAutenticado UserId actor, @PathVariable String clave,
                                                  @RequestBody @Valid CambioDeFuncionalidadRequest cuerpo,
                                                  HttpServletRequest request) {
        return FuncionalidadEnConsolaResponse.from(cambiar.cambiar(new CambiarFuncionalidadCommand(
                PedidoDeConsola.acceso(actor, request), clave, cuerpo.prendidaGlobal(), cuerpo.porRol(),
                cuerpo.aviso(), cuerpo.motivo(), cuerpo.version(), request.getHeader(PedidoDeConsola.REAUTENTICACION),
                PedidoDeConsola.dispositivo(request))));
    }
}
