package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Reemplaza POST /api/v1/account-requests. Comando SIN campo role: CLAUDE.MD §5.3.3.
 *
 * <p>Sin {@code supabaseUserId}: hasta el 2026-08-27 el cliente tenia que crear primero un
 * usuario en Supabase Auth y mandar su id acá — resabio de antes de D-49. Ahora que Renaser
 * OS es dueno de su propia identidad de punta a punta, el UUID del solicitante lo genera
 * {@code AccountRequestService.submit} internamente (ver ese javadoc).
 *
 * <p>Con {@code verificationToken} (2026-08-27): el reemplazo propio del email de un solo uso
 * que antes emitia Supabase Auth al verificar el OTP — ver
 * {@code ConfirmarCodigoVerificacionEmailUseCase}. Sin este campo, cualquiera podia mandar una
 * solicitud con un correo ajeno o inventado sin que nadie lo notara hasta la aprobacion manual.
 */
public interface SubmitAccountRequestUseCase {

    AccountRequestId submit(SubmitAccountRequestCommand command);

    record SubmitAccountRequestCommand(
            @NotBlank @Email String email,
            @NotBlank String fullName,
            @NotBlank String phone,
            String city,
            @NotBlank String verificationToken,
            String requestIp) {

        public SubmitAccountRequestCommand {
            SelfValidating.validateConstructorArgs(SubmitAccountRequestCommand.class,
                    email, fullName, phone, city, verificationToken, requestIp);
        }
    }
}
