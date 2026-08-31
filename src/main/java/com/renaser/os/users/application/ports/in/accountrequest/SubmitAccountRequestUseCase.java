package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Reemplaza POST /api/v1/account-requests. Comando SIN campo role: CLAUDE.MD §5.3.3.
 *
 * <p>El comando no recibe ningun id de usuario: hasta el 2026-08-27 el cliente tenia que crear
 * primero un usuario en Supabase Auth y mandar ese id acá (el campo se llamaba
 * {@code supabaseUserId}) — resabio de antes de D-49. Ahora que Renaser
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
            String requestIp,
            /**
             * NULL los dos en el alta por formulario. En el alta por proveedor social llevan la
             * identidad que {@code AutenticacionSocialService} acaba de verificar contra
             * Google/Apple/Facebook, para que {@code approve()} pueda crear la
             * {@code IdentidadExterna} en la misma transaccion (A-7).
             *
             * <p>Estos dos campos NO existen en {@code SubmitAccountRequestRequest}, el DTO del
             * alta publica, y no pueden existir: si el cliente pudiera mandar un
             * {@code sujetoProveedor}, cualquiera reclamaria la identidad social de otro con solo
             * conocer su {@code sub}. Viajan servidor a servidor, de un caso de uso al otro, sin
             * pasar nunca por HTTP — mismo blindaje por compilador que el {@code role} ausente de
             * CLAUDE.MD §5.3.3.
             */
            ProveedorIdentidad proveedor,
            String sujetoProveedor) {

        public SubmitAccountRequestCommand {
            SelfValidating.validateConstructorArgs(SubmitAccountRequestCommand.class,
                    email, fullName, phone, city, verificationToken, contrasena, requestIp,
                    proveedor, sujetoProveedor);
            requireProveedorYSujetoJuntos(proveedor, sujetoProveedor);
        }

        /**
         * Alta por formulario: la unica forma que el adaptador web tiene de armar este comando.
         * No recibe {@code proveedor} ni {@code sujetoProveedor} — no es que los mande en null,
         * es que no existe el parametro donde ponerlos. Ese es el blindaje: si el cliente
         * pudiera hacer llegar un {@code sujetoProveedor}, cualquiera reclamaria la identidad
         * social de otro con solo conocer su {@code sub}. Lo impide el compilador, no un
         * {@code if} (CLAUDE.MD §5.3.3, mismo principio que el {@code role} ausente).
         */
        public static SubmitAccountRequestCommand porFormulario(String email, String fullName, String phone,
                                                                 String city, String verificationToken,
                                                                 String contrasena, String requestIp) {
            return new SubmitAccountRequestCommand(email, fullName, phone, city, verificationToken,
                    contrasena, requestIp, null, null);
        }

        /**
         * Alta abierta por un proveedor social. Solo la arma {@code AutenticacionSocialService},
         * con una identidad que acaba de verificar el mismo contra Google/Apple — nunca con algo
         * que haya viajado por HTTP. Sin contrasena: esa cuenta entra por el proveedor.
         */
        public static SubmitAccountRequestCommand porProveedorSocial(String email, String fullName, String phone,
                                                                      String city, String verificationToken,
                                                                      String requestIp, ProveedorIdentidad proveedor,
                                                                      String sujetoProveedor) {
            return new SubmitAccountRequestCommand(email, fullName, phone, city, verificationToken,
                    null, requestIp, proveedor, sujetoProveedor);
        }

        /**
         * Un proveedor sin sujeto no identifica a nadie y un sujeto sin proveedor es ambiguo: o
         * vienen los dos, o ninguno. Misma regla que {@code OrigenSocial} en el dominio y que el
         * CHECK de la migracion V12 — se valida aca ademas porque este comando puede construirse
         * desde un scheduler o un listener, no solo desde el controller (CLAUDE.MD §5.4.3).
         */
        private static void requireProveedorYSujetoJuntos(ProveedorIdentidad proveedor, String sujetoProveedor) {
            boolean hayProveedor = proveedor != null;
            boolean haySujeto = sujetoProveedor != null && !sujetoProveedor.isBlank();
            if (hayProveedor != haySujeto) {
                throw new IllegalArgumentException(
                        "proveedor y sujetoProveedor viajan juntos: o vienen ambos, o ninguno");
            }
        }

        @Override
        public String toString() {
            return "SubmitAccountRequestCommand[email=" + email + ", fullName=" + fullName
                    + ", verificationToken=oculto, contrasena=oculta, proveedor=" + proveedor
                    + ", sujetoProveedor=oculto]";
        }
    }
}
