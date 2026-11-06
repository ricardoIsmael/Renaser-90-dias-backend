package com.renaser.os.users.application.ports.in.autenticacion;

import com.renaser.os.shared.application.SelfValidating;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Pedir el codigo de verificacion por WhatsApp cuando no llega por correo (spec 043, D-325). Dos caminos: el alta (al WhatsApp que la persona
 * acaba de escribir en «Solicitar acceso») y la recuperacion de la contraseña (SOLO al WhatsApp que la cuenta ya tiene guardado: nunca a un numero
 * que se escriba en ese momento, porque eso permitiria robar cuentas).
 */
public interface PedirCodigoPorWhatsAppUseCase {

    /** ¿Se puede ofrecer? Hay con que enviar (clave, numero emisor y plantilla). El interruptor {@code auth.codigo_whatsapp} lo corta antes. */
    boolean disponible();

    /**
     * El codigo del alta al WhatsApp que se escribio. Es EL MISMO del correo mientras siga vigente.
     *
     * @return el numero enmascarado al que se mando ({@code +51 9•• ••• •21})
     */
    String paraElRegistro(CodigoDelRegistroCommand command);

    /** El codigo de la recuperacion al WhatsApp guardado de la cuenta. No devuelve nada: la respuesta es la misma exista o no la cuenta (FR-009). */
    void paraRecuperar(CodigoDeRecuperacionCommand command);

    record CodigoDelRegistroCommand(@NotBlank @Email String email, @NotBlank String whatsapp, String requestIp) {
        public CodigoDelRegistroCommand {
            SelfValidating.validateConstructorArgs(CodigoDelRegistroCommand.class, email, whatsapp, requestIp);
        }

        /** El numero no se imprime entero. */
        @Override
        public String toString() {
            return "CodigoDelRegistroCommand[email=" + email + ", whatsapp=oculto]";
        }
    }

    record CodigoDeRecuperacionCommand(@NotBlank @Email String email, String requestIp) {
        public CodigoDeRecuperacionCommand {
            SelfValidating.validateConstructorArgs(CodigoDeRecuperacionCommand.class, email, requestIp);
        }
    }
}
