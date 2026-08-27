package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase.ApproveAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.CheckAccountRequestStatusUseCase;
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
import org.springframework.web.bind.annotation.RequestHeader;
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
 * X-Actor-Id: header TEMPORAL. B-2 (claves JWT RS256 de Supabase) sigue sin confirmar,
 * asi que todavia no hay Resource Server real resolviendo el actor desde un Bearer token.
 * Reemplazar por @AuthenticationPrincipal apenas B-2 se resuelva — NO USAR EN PRODUCCION.
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

    public AccountRequestController(SubmitAccountRequestUseCase submitUseCase,
                                     ApproveAccountRequestUseCase approveUseCase,
                                     RejectAccountRequestUseCase rejectUseCase,
                                     ListAccountRequestsUseCase listUseCase,
                                     DeleteAccountRequestUseCase deleteUseCase,
                                     CheckAccountRequestStatusUseCase checkStatusUseCase) {
        this.submitUseCase = submitUseCase;
        this.approveUseCase = approveUseCase;
        this.rejectUseCase = rejectUseCase;
        this.listUseCase = listUseCase;
        this.deleteUseCase = deleteUseCase;
        this.checkStatusUseCase = checkStatusUseCase;
    }

    @PostMapping
    public ResponseEntity<AccountRequestIdResponse> submit(@RequestBody @Valid SubmitAccountRequestRequest request,
                                                             HttpServletRequest httpRequest) {
        AccountRequestId id = submitUseCase.submit(new SubmitAccountRequestCommand(
                request.email(), request.fullName(), request.phone(),
                request.city(), httpRequest.getRemoteAddr()));
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(new AccountRequestIdResponse(id.value()));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<Void> approve(@PathVariable UUID id, @RequestHeader("X-Actor-Id") String actorId) {
        approveUseCase.approve(new ApproveAccountRequestCommand(AccountRequestId.of(id), UserId.of(actorId)));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<Void> reject(@PathVariable UUID id, @RequestHeader("X-Actor-Id") String actorId,
                                        @RequestBody @Valid RejectAccountRequestRequest request) {
        rejectUseCase.reject(new RejectAccountRequestCommand(AccountRequestId.of(id), UserId.of(actorId),
                request.reason()));
        return ResponseEntity.noContent().build();
    }

    /** Panel admin (gap #9): ADMIN/ALCHEMIST — gate DENTRO del servicio (CLAUDE.MD §5.4.6). */
    @GetMapping
    public AccountRequestPageResponse listar(@RequestHeader("X-Actor-Id") String actorId,
                                              @RequestParam(required = false) AccountRequestStatus status,
                                              @RequestParam(defaultValue = "0") int page,
                                              @RequestParam(defaultValue = "20") int size) {
        var pagina = listUseCase.listar(new ListAccountRequestsCommand(UserId.of(actorId), status, page, size));
        return AccountRequestPageResponse.from(pagina);
    }

    /** Panel admin (gap #9): borrar una solicitud (cualquier estado, ver javadoc de {@link DeleteAccountRequestUseCase}). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable UUID id, @RequestHeader("X-Actor-Id") String actorId) {
        deleteUseCase.eliminar(new DeleteAccountRequestCommand(UserId.of(actorId), AccountRequestId.of(id)));
        return ResponseEntity.noContent().build();
    }

    /**
     * PUBLIC_ENDPOINT (gap #9): "mi solicitud" — el solicitante todavia no tiene `User`
     * ni `X-Actor-Id` posible, se resuelve por el id que ya guardo del 202 de {@code submit}
     * (ver javadoc de {@link CheckAccountRequestStatusUseCase}).
     */
    @GetMapping("/{id}/status")
    public AccountRequestStatusResponse consultarEstado(@PathVariable UUID id) {
        var vista = checkStatusUseCase.consultar(AccountRequestId.of(id));
        return new AccountRequestStatusResponse(vista.status(), vista.rejectionReason());
    }
}
