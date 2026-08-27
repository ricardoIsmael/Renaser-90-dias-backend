package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase.ApproveAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.CheckAccountRequestStatusUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ConsultarEmailRegistradoUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.VerificarDominioEmailUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.DeleteAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.DeleteAccountRequestUseCase.DeleteAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.ListAccountRequestsUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ListAccountRequestsUseCase.ListAccountRequestsCommand;
import com.renaser.os.users.application.ports.in.accountrequest.RejectAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.RejectAccountRequestUseCase.RejectAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase.SubmitAccountRequestCommand;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * PUBLIC_ENDPOINT: POST /account-requests (autoregistro, sin autenticar).
 * Los otros dos ({approve}, {reject}) requieren ADMIN/ALCHEMIST — hoy verificado
 * DENTRO del caso de uso (User.canManageRoles(), via AccountRequest.approve/reject),
 * NO todavia con @RequiresPermission + AccessGuard de CLAUDE.MD §0.bis: ese mecanismo
 * necesita el enum Permission, bloqueado por R-2 (que permisos tiene MENTOR_LEAD).
 *
 * Actor: {@code @ActorAutenticado UserId} lo resuelve desde la sesion propia (D-49,
 * docs/MODULO_AUTH.md), con respaldo por el header TEMPORAL {@code X-Actor-Id} mientras
 * queden clientes que no manden sesion. Ese header no se usa en produccion: es solo el
 * puente de la migracion, y desaparece cuando el respaldo del resolver se retire.
 */
@RestController
@RequestMapping("/api/v1/account-requests")
public class AccountRequestController {

    private final SubmitAccountRequestUseCase submitUseCase;
    private final ApproveAccountRequestUseCase approveUseCase;
    private final RejectAccountRequestUseCase rejectUseCase;
    private final ListAccountRequestsUseCase listUseCase;
    private final DeleteAccountRequestUseCase deleteUseCase;
    private final CheckAccountRequestStatusUseCase checkStatusUseCase;
    private final ConsultarEmailRegistradoUseCase consultarEmailRegistradoUseCase;
    private final VerificarDominioEmailUseCase verificarDominioEmailUseCase;

    public AccountRequestController(SubmitAccountRequestUseCase submitUseCase,
                                     ApproveAccountRequestUseCase approveUseCase,
                                     RejectAccountRequestUseCase rejectUseCase,
                                     ListAccountRequestsUseCase listUseCase,
                                     DeleteAccountRequestUseCase deleteUseCase,
                                     CheckAccountRequestStatusUseCase checkStatusUseCase,
                                     ConsultarEmailRegistradoUseCase consultarEmailRegistradoUseCase,
                                     VerificarDominioEmailUseCase verificarDominioEmailUseCase) {
        this.submitUseCase = submitUseCase;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
        this.listUseCase = listUseCase;
        this.deleteUseCase = deleteUseCase;
        this.checkStatusUseCase = checkStatusUseCase;
        this.consultarEmailRegistradoUseCase = consultarEmailRegistradoUseCase;
        this.verificarDominioEmailUseCase = verificarDominioEmailUseCase;
    }

    /**
     * PUBLIC_ENDPOINT. ¿Se puede registrar con este correo? Lo consulta el formulario mientras
     * se escribe, para no descubrir al final que el correo ya existe.
     */
    @PostMapping("/check-email")
    public DisponibilidadEmailResponse checkEmail(@RequestBody @Valid ConsultarEmailRequest request,
                                                   HttpServletRequest httpRequest) {
        return new DisponibilidadEmailResponse(
                !consultarEmailRegistradoUseCase.estaRegistrado(request.email(), httpRequest.getRemoteAddr()));
    }

    /**
     * PUBLIC_ENDPOINT. La pregunta inversa de {@link #checkEmail}, para "olvide mi contrasena".
     * Misma consulta, distinto nombre de la respuesta — ver {@link ExistenciaCuentaResponse}.
     */
    @PostMapping("/exists")
    public ExistenciaCuentaResponse exists(@RequestBody @Valid ConsultarEmailRequest request,
                                            HttpServletRequest httpRequest) {
        return new ExistenciaCuentaResponse(
                consultarEmailRegistradoUseCase.estaRegistrado(request.email(), httpRequest.getRemoteAddr()));
    }

    /** PUBLIC_ENDPOINT. ¿El dominio del correo puede recibir correo? Aviso, nunca un bloqueo. */
    @PostMapping("/verify-email")
    public VerificacionDominioResponse verifyEmail(@RequestBody @Valid ConsultarEmailRequest request) {
        return VerificacionDominioResponse.from(verificarDominioEmailUseCase.verificar(request.email()));
    }

    @PostMapping
    public ResponseEntity<AccountRequestIdResponse> submit(@RequestBody @Valid SubmitAccountRequestRequest request,
                                                             HttpServletRequest httpRequest) {
        AccountRequestId id = submitUseCase.submit(new SubmitAccountRequestCommand(
                request.email(), request.fullName(), request.phone(),
                request.city(), request.verificationToken(), request.contrasena(),
                httpRequest.getRemoteAddr()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new AccountRequestIdResponse(id.value()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id, @ActorAutenticado UserId actor) {
        approveUseCase.approve(new ApproveAccountRequestCommand(AccountRequestId.of(id), actor));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id, @ActorAutenticado UserId actor,
                                        @RequestBody @Valid RejectAccountRequestRequest request) {
        rejectUseCase.reject(new RejectAccountRequestCommand(AccountRequestId.of(id), actor,
                request.reason()));
        return ResponseEntity.noContent().build();
    }

    /** Panel admin (gap #9): ADMIN/ALCHEMIST — gate DENTRO del servicio (CLAUDE.MD §5.4.6). */
    @GetMapping
    public AccountRequestPageResponse listar(@ActorAutenticado UserId actor,
                                              @RequestParam(required = false) AccountRequestStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        var pagina = listUseCase.listar(new ListAccountRequestsCommand(actor, status, page, size));
        return AccountRequestPageResponse.from(pagina);
    }

    /** Panel admin (gap #9): borrar una solicitud (cualquier estado, ver javadoc de {@link DeleteAccountRequestUseCase}). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, @ActorAutenticado UserId actor) {
        deleteUseCase.eliminar(new DeleteAccountRequestCommand(actor, AccountRequestId.of(id)));
        return ResponseEntity.noContent().build();
    }

    /**
     * PUBLIC_ENDPOINT (gap #9): "mi solicitud" — el solicitante todavia no tiene `User`,
     * asi que no hay actor posible: se resuelve por el id que ya guardo del 202 de {@code submit}
     * (ver javadoc de {@link CheckAccountRequestStatusUseCase}).
     */
    @GetMapping("/{id}/status")
    public AccountRequestStatusResponse consultarEstado(@PathVariable UUID id) {
        var vista = checkStatusUseCase.consultar(AccountRequestId.of(id));
        return new AccountRequestStatusResponse(vista.status(), vista.rejectionReason());
    }
}
