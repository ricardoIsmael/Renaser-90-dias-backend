package com.renaser.os.users.application.ports.in.whatsapp;

import com.renaser.os.shared.domain.UserId;

import java.util.Objects;
import java.util.Optional;

/**
 * Yo → «WhatsApp» (spec 051): cualquier cuenta —personal o aprendiz— agrega o cambia su numero de WhatsApp, y <b>solo se guarda despues de
 * confirmarlo</b> con un codigo de 6 digitos que llega por WhatsApp a ese numero. Ese numero confirmado es el que usa la recuperacion de la
 * contraseña (spec 043). Siempre sobre la cuenta de la sesion: ningun metodo recibe el id de otra persona desde afuera.
 */
public interface WhatsappConfirmadoUseCase {

    /** Lo que Yo necesita para dibujar la fila: si se puede ofrecer y el numero confirmado, enmascarado. */
    Estado estado(UserId cuenta);

    /**
     * Manda el codigo al numero escrito. No guarda nada.
     *
     * @return el numero enmascarado al que se mando ({@code +51 9•• ••• •21})
     */
    String pedirCodigo(PedirCodigoCommand command);

    /**
     * Comprueba el codigo y, si es el de ese numero, lo guarda como el numero confirmado de la cuenta (reemplaza al anterior).
     *
     * @return el numero confirmado, enmascarado
     */
    String confirmar(ConfirmarCommand command);

    /** @param disponible hay con que mandar codigos (clave, numero emisor y plantilla) @param confirmado el numero enmascarado, o vacio */
    record Estado(boolean disponible, Optional<String> confirmado) {
    }

    record PedirCodigoCommand(UserId cuenta, String whatsapp, String requestIp) {
        public PedirCodigoCommand {
            Objects.requireNonNull(cuenta, "cuenta es obligatoria");
        }

        /** El numero no se imprime entero. */
        @Override
        public String toString() {
            return "PedirCodigoCommand[cuenta=" + cuenta + ", whatsapp=oculto]";
        }
    }

    record ConfirmarCommand(UserId cuenta, String whatsapp, String codigo, String requestIp) {
        public ConfirmarCommand {
            Objects.requireNonNull(cuenta, "cuenta es obligatoria");
        }

        /** Ni el numero ni el codigo se imprimen. */
        @Override
        public String toString() {
            return "ConfirmarCommand[cuenta=" + cuenta + ", whatsapp=oculto, codigo=oculto]";
        }
    }
}
