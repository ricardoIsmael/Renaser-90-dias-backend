package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

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
 *
 * <p>Con {@code contrasena} (2026-08-27): la persona elige su clave al registrarse, no despues
 * de que la aprueben. Antes el alta no capturaba contrasena y hacia falta un SEGUNDO correo
 * ("activa tu cuenta") para fijarla — dos envios en el camino critico, y quien no recibia el
 * segundo quedaba varado sin saber por que. Ahora la fila de {@code usuarios} se crea aca en
 * estado {@code INACTIVE} con su hash, y aprobar solo la activa.
 */
public interface SubmitAccountRequestUseCase {

    AccountRequestId submit(SubmitAccountRequestCommand command);

    /**
     * {@code contrasena} viaja EN CLARO por este comando (unica vez que existe en texto plano —
     * se hashea antes de guardarse, nunca se persiste asi) y por eso {@link #toString()} la
     * oculta: un log del comando no puede filtrar la credencial (CLAUDE.MD §5.4.9).
     */
    record SubmitAccountRequestCommand(
            @NotBlank @Email String email,
            @NotBlank String fullName,
            @NotBlank String phone,
            String city,
            @NotBlank String verificationToken,
            /**
             * NULL en el alta por proveedor social (Google/Apple): esa cuenta entra por el
             * proveedor y no tiene contrasena propia — es exactamente el caso para el que
             * {@code usuarios.hash_contrasena} ya era nullable. Obligatoria, en cambio, en el
             * alta por formulario: la exige {@code SubmitAccountRequestRequest} en el borde
             * web, que es donde se sabe por que puerta entro la persona (CLAUDE.MD §5.4.3:
             * la validacion sintactica es del adaptador, la semantica del dominio).
             *
             * <p>Si viene, el minimo es 12 — el mismo de {@code ConfirmarResetContrasenaCommand}
             * y del login.
             */
            @Size(min = 12, max = 200) String contrasena,
            String requestIp) {

        public SubmitAccountRequestCommand {
            SelfValidating.validateConstructorArgs(SubmitAccountRequestCommand.class,
                    email, fullName, phone, city, verificationToken, contrasena, requestIp);
        }

        @Override
        public String toString() {
            return "SubmitAccountRequestCommand[email=" + email + ", fullName=" + fullName
                    + ", verificationToken=oculto, contrasena=oculta]";
        }
    }
}
