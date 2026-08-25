package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Reemplaza POST /api/v1/account-requests. Comando SIN campo role: CLAUDE.MD §5.3.3. */
public interface SubmitAccountRequestUseCase {

    AccountRequestId submit(SubmitAccountRequestCommand command);

    record SubmitAccountRequestCommand(
            @NotBlank String supabaseUserId,
            @NotBlank @Email String email,
            @NotBlank String fullName,
            @NotBlank String phone,
            String city,
            String requestIp) {

        public SubmitAccountRequestCommand {
            SelfValidating.validateConstructorArgs(SubmitAccountRequestCommand.class,
                    supabaseUserId, email, fullName, phone, city, requestIp);
        }
    }
}
