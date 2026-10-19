package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.VerComoUseCase;
import com.renaser.os.plataforma.application.ports.in.VerComoUseCase.AbrirVerComoCommand;
import com.renaser.os.plataforma.application.ports.in.VerComoUseCase.CerrarVerComoCommand;
import com.renaser.os.plataforma.application.ports.in.VerComoUseCase.VerComoAbierto;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * «Ver como rol» desde la consola Soporte TI (spec 002, fase 2, US5; contrato fase-2.md §5; D-273).
 *
 * <ul>
 *   <li>{@code POST /api/v1/soporte-ti/ver-como}: lo pide Soporte TI con SU sesión y la consola desbloqueada
 *       ({@code OPERATE_PLATFORM} estricto, {@code X-Consola-Token}: 403 / 423). Responde 200 con la cuenta y el
 *       vencimiento, y el id de una sesión NUEVA de la cuenta de prueba en la cabecera {@code X-Auth-Token}. La sesión
 *       con la que se pidió sigue sirviendo.</li>
 *   <li>{@code DELETE /api/v1/soporte-ti/ver-como}: lo pide la app CON LA SESIÓN «ver como» (la de la cuenta de
 *       prueba, que no es Soporte TI: por eso {@code USE_APP} y no {@code OPERATE_PLATFORM}). Cierra esa sesión y
 *       responde 204; con una sesión que no es de «ver como», 404 y no cierra nada. Es una de las dos escrituras que el
 *       filtro de solo lectura deja pasar.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/soporte-ti/ver-como")
@SinInterruptor(motivo = "«Ver como» es parte de la consola Soporte TI y salir de él tiene que funcionar siempre "
        + "(anti-bloqueo, FR-004)")
class VerComoSoporteTiController {

    static final String CABECERA_DE_SESION = "X-Auth-Token";

    private final VerComoUseCase verComo;

    VerComoSoporteTiController(VerComoUseCase verComo) {
        this.verComo = verComo;
    }

    @PostMapping
    @RequiresPermission(Permission.OPERATE_PLATFORM)
    public VerComoResponse abrir(@ActorAutenticado UserId actor, @RequestBody VerComoRequest cuerpo,
                                 HttpServletRequest request, HttpServletResponse response) {
        VerComoAbierto abierto = verComo.abrir(new AbrirVerComoCommand(PedidoDeConsola.acceso(actor, request),
                cuerpo.rol(), cuerpo.rolPersonalizadoId(), PedidoDeConsola.dispositivo(request)));
        response.setHeader(CABECERA_DE_SESION, abierto.token());
        return VerComoResponse.from(abierto);
    }

    /**
     * Después de auditar, la sesión del pedido se invalida por la {@code HttpSession}: si solo se borrara en Redis, el
     * filtro de Spring Session la volvería a guardar al terminar el pedido (con la hora del último acceso).
     */
    @DeleteMapping
    @RequiresPermission(Permission.USE_APP)
    public ResponseEntity<Void> cerrar(HttpServletRequest request) {
        HttpSession sesion = request.getSession(false);
        verComo.cerrar(new CerrarVerComoCommand(sesion == null ? null : sesion.getId(),
                PedidoDeConsola.dispositivo(request)));
        if (sesion != null) {
            sesion.invalidate();
        }
        return ResponseEntity.noContent().build();
    }
}
