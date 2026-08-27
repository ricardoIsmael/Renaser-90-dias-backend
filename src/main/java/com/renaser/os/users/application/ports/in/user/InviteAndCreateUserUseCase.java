package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** Via para MENTOR/ADMIN/ALCHEMIST y cohortes a mitad de programa (CLAUDE.MD §4.3). */
public interface InviteAndCreateUserUseCase {

    UserId invite(InviteUserCommand command);

    /**
     * Panel admin de staff (gap #6): a diferencia de {@link #invite}, no recibe un id
     * externo (D-49: ya no hay Supabase de donde tomarlo, el backend es dueño de su
     * propia identidad) — el id lo genera la implementacion. Restringido a roles de
     * staff (nunca TRAINEE, ver {@code ListStaffUseCase.ROLES_STAFF}): un aprendiz se da
     * de alta por {@code AccountRequest}, no por invitacion admin. Genera ademas una
     * contrasena temporal y la envia por {@code EnviarEmailPort} — sin esto el invitado
     * no tendria forma de autenticarse (D-49 ya no delega esto a un proveedor externo).
     */
    UserId inviteStaff(InviteStaffCommand command);

    record InviteUserCommand(
            @NotBlank String supabaseUserId,
            @NotBlank @Email String email,
            @NotBlank String fullName,
            @NotNull UserRole role,
            @NotNull UserId actorId) {

        public InviteUserCommand {
            SelfValidating.validateConstructorArgs(InviteUserCommand.class,
                    supabaseUserId, email, fullName, role, actorId);
        }
    }

    record InviteStaffCommand(
            @NotBlank @Email String email,
            @NotBlank String fullName,
            @NotNull UserRole role,
            @NotNull UserId actorId) {

        public InviteStaffCommand {
            SelfValidating.validateConstructorArgs(InviteStaffCommand.class, email, fullName, role, actorId);
            if (role == UserRole.TRAINEE) {
                throw new IllegalArgumentException(
                        "La invitacion de staff no admite el rol TRAINEE (se da de alta via AccountRequest)");
            }
        }
    }
}
