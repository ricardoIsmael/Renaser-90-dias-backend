package com.renaser.os.onboarding.infrastructure.adapter.in.rest.grabacionv90;

import com.renaser.os.onboarding.application.ports.in.grabacionv90.ListarGrabacionesV90UseCase;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.RegistrarGrabacionV90UseCase;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.RegistrarGrabacionV90UseCase.RegistrarGrabacionV90Command;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase.ConsultarEstadoV90Query;
import com.renaser.os.onboarding.application.ports.in.grabacionv90.ValidarV90UseCase.SolicitarValidacionV90Command;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * <b>Apagado por defecto (decision del dueno, 2026-09-03).</b> Las grabaciones V90 salieron del
 * alcance junto con las demas validaciones automaticas, hasta que exista una via de aprendizaje
 * automatico que las sostenga. Sin la propiedad {@code renaser.onboarding.v90.habilitado=true},
 * este bean no se crea y las cuatro rutas dejan de existir.
 *
 * <p>Se apaga por propiedad y no borrando codigo a proposito: los 23 archivos de V90, su tabla y
 * su maquina de estados quedan intactos, asi que el dia que se retome no hay que reconstruir
 * nada — se prende la propiedad. Es el mismo mecanismo que ya usan el almacenamiento
 * ({@code renaser.storage.proveedor}) y el correo ({@code renaser.email.proveedor}) en este
 * repo; no se invento uno nuevo.
 *
 * <p>{@code matchIfMissing = false}: ausente significa apagado. Sin tests que golpeen estas
 * rutas (verificado), apagarlo no rompe la suite.
 */
@ConditionalOnProperty(name = "renaser.onboarding.v90.habilitado", havingValue = "true")
@RestController
@RequestMapping("/api/v1/onboarding/v90-recordings")
public class GrabacionV90Controller {

    private final RegistrarGrabacionV90UseCase registrarUseCase;
    private final ListarGrabacionesV90UseCase listarUseCase;
    private final ValidarV90UseCase validarUseCase;

    public GrabacionV90Controller(RegistrarGrabacionV90UseCase registrarUseCase,
                                   ListarGrabacionesV90UseCase listarUseCase, ValidarV90UseCase validarUseCase) {
        this.registrarUseCase = registrarUseCase;
        this.listarUseCase = listarUseCase;
        this.validarUseCase = validarUseCase;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "la media referenciada tiene que ser del propio actor")
    @PostMapping
    public ResponseEntity<GrabacionV90Response> registrar(@ActorAutenticado UserId actor,
                                                            @Valid @RequestBody RegistrarGrabacionV90Request request) {
        var comando = new RegistrarGrabacionV90Command(actor, request.phase(), request.axis(),
                request.index(), request.questionKey(), request.mediaId(), request.durationSeconds(),
                request.transcript());
        var grabacion = registrarUseCase.registrar(comando);
        return ResponseEntity.status(HttpStatus.CREATED).body(GrabacionV90Response.from(grabacion));
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping
    public List<GrabacionV90Response> listar(@ActorAutenticado UserId actor) {
        return listarUseCase.listar(actor).stream().map(GrabacionV90Response::from).toList();
    }

    /** 202 de inmediato: el trabajo real corre async (CLAUDE.MD §7). */
    @RequiresPermission(value = Permission.USE_APP, scope = "dueno de la grabacion")
    @PostMapping("/{id}/validation")
    public ResponseEntity<ValidacionV90Response> solicitarValidacion(@ActorAutenticado UserId actor,
                                                                       @PathVariable("id") Long id) {
        validarUseCase.solicitarValidacion(new SolicitarValidacionV90Command(actor, id));
        return ResponseEntity.accepted().body(ValidacionV90Response.accepted());
    }

    /** GET de polling. */
    @RequiresPermission(value = Permission.USE_APP, scope = "dueno de la grabacion")
    @GetMapping("/{id}/validation")
    public ValidacionV90Response consultarValidacion(@ActorAutenticado UserId actor,
                                                       @PathVariable("id") Long id) {
        var estado = validarUseCase.consultarEstado(new ConsultarEstadoV90Query(actor, id));
        return ValidacionV90Response.from(estado);
    }
}
