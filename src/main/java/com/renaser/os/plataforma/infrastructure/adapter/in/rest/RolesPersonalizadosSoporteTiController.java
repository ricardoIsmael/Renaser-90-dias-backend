package com.renaser.os.plataforma.infrastructure.adapter.in.rest;

import com.renaser.os.plataforma.application.ports.in.GestionarRolesEnConsolaUseCase;
import com.renaser.os.plataforma.application.ports.in.GestionarRolesEnConsolaUseCase.BorrarRolEnConsolaCommand;
import com.renaser.os.plataforma.application.ports.in.GestionarRolesEnConsolaUseCase.CrearRolEnConsolaCommand;
import com.renaser.os.plataforma.application.ports.in.GestionarRolesEnConsolaUseCase.EditarRolEnConsolaCommand;
import com.renaser.os.plataforma.application.ports.in.GestionarRolesEnConsolaUseCase.ReemplazarRolEnConsolaCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.ApiErrorConCodigo;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.shared.web.security.SinInterruptor;
import com.renaser.os.users.api.ConflictoDeRolPersonalizadoException;
import com.renaser.os.users.api.RolPersonalizadoRechazadoException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Los roles personalizados en la consola Soporte TI (spec 002, US7; contrato de la fase 2, §4.5; D-272): listar, crear,
 * editar (también desactivar y reactivar), borrar y reemplazar en bloque. Mismos cerrojos que la fase 1: solo
 * IT_SUPPORT ({@code OPERATE_PLATFORM}, estricto en el interceptor y repetido en el servicio), {@code X-Consola-Token}
 * vivo (423) y, para borrar y reemplazar en bloque, {@code X-Reautenticacion} (428).
 */
@RestController
@RequestMapping("/api/v1/soporte-ti/roles")
@RequiresPermission(Permission.OPERATE_PLATFORM)
@SinInterruptor(motivo = "La consola Soporte TI no se puede apagar: es desde donde se prende todo lo demás "
        + "(anti-bloqueo, FR-004)")
class RolesPersonalizadosSoporteTiController {

    private final GestionarRolesEnConsolaUseCase roles;

    RolesPersonalizadosSoporteTiController(GestionarRolesEnConsolaUseCase roles) {
        this.roles = roles;
    }

    @GetMapping
    public List<RolPersonalizadoEnConsolaResponse> listar(@ActorAutenticado UserId actor, HttpServletRequest request) {
        return roles.listar(PedidoDeConsola.acceso(actor, request)).stream()
                .map(RolPersonalizadoEnConsolaResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<RolPersonalizadoEnConsolaResponse> crear(@ActorAutenticado UserId actor,
                                                                   @RequestBody RolPersonalizadoRequests.Crear cuerpo,
                                                                   HttpServletRequest request) {
        var creado = roles.crear(new CrearRolEnConsolaCommand(PedidoDeConsola.acceso(actor, request), cuerpo.nombre(),
                cuerpo.descripcion(), cuerpo.rolBase(), cuerpo.funcionalidades(), cuerpo.motivo(),
                PedidoDeConsola.dispositivo(request)));
        return ResponseEntity.status(HttpStatus.CREATED).body(RolPersonalizadoEnConsolaResponse.from(creado));
    }

    @PutMapping("/{id}")
    public RolPersonalizadoEnConsolaResponse editar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                                    @RequestBody RolPersonalizadoRequests.Editar cuerpo,
                                                    HttpServletRequest request) {
        return RolPersonalizadoEnConsolaResponse.from(roles.editar(new EditarRolEnConsolaCommand(
                PedidoDeConsola.acceso(actor, request), id, cuerpo.nombre(), cuerpo.descripcion(), cuerpo.rolBase(),
                cuerpo.funcionalidades(), cuerpo.activo(), cuerpo.motivo(), cuerpo.version(),
                PedidoDeConsola.dispositivo(request))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> borrar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                       @RequestParam(required = false) String motivo, HttpServletRequest request) {
        roles.borrar(new BorrarRolEnConsolaCommand(PedidoDeConsola.acceso(actor, request), id, motivo,
                request.getHeader(PedidoDeConsola.REAUTENTICACION), PedidoDeConsola.dispositivo(request)));
        return ResponseEntity.noContent().build();
    }

    /**
     * 409 con {@code codigo} ({@code VERSION_CAMBIADA}, {@code ROL_CON_PERSONAS}): la app sabe qué pasó sin releer el rol
     * ni leer el texto (contrato fase 2 §4, aditivo).
     */
    @ExceptionHandler(ConflictoDeRolPersonalizadoException.class)
    public ResponseEntity<ApiErrorConCodigo> conflicto(ConflictoDeRolPersonalizadoException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorConCodigo.of(ex.getMessage(), ex.codigo(), null));
    }

    /** 400 con {@code codigo} ({@code NOMBRE_REPETIDO}, {@code MAXIMO_DE_ROLES}), como dice el contrato (§4.5). */
    @ExceptionHandler(RolPersonalizadoRechazadoException.class)
    public ResponseEntity<ApiErrorConCodigo> rechazado(RolPersonalizadoRechazadoException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorConCodigo.of(ex.getMessage(), ex.codigo(), null));
    }

    @PostMapping("/{id}/reemplazar")
    public RolPersonalizadoRequests.Reemplazados reemplazar(@ActorAutenticado UserId actor, @PathVariable UUID id,
                                                           @RequestBody RolPersonalizadoRequests.Reemplazar cuerpo,
                                                           HttpServletRequest request) {
        return new RolPersonalizadoRequests.Reemplazados(roles.reemplazar(new ReemplazarRolEnConsolaCommand(
                PedidoDeConsola.acceso(actor, request), id, cuerpo.rolDestinoId(), cuerpo.motivo(),
                request.getHeader(PedidoDeConsola.REAUTENTICACION), PedidoDeConsola.dispositivo(request))));
    }
}
