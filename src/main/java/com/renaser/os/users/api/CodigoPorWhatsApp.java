package com.renaser.os.users.api;

import java.util.Optional;

/**
 * Mandar el codigo de verificacion por WhatsApp (spec 043, D-325). <b>Lo implementa {@code notifications}</b>, dueño del canal de WhatsApp
 * (YCloud, spec 041), y lo consume {@code users} para el alta y la recuperacion de la contraseña: la dependencia va de {@code notifications}
 * hacia {@code users}, como siempre, y {@code users} no importa a nadie (el mismo patron de {@link WhatsappDeLaFichaFinder}).
 *
 * <p>El codigo viaja en la plantilla de categoria AUTENTICACION ({@code WHATSAPP_PLANTILLA_CODIGO}). El numero completo nunca se escribe en un
 * registro ni sale en una respuesta: solo {@link NumeroParaCodigo#enmascarado()}.
 */
public interface CodigoPorWhatsApp {

    /** Hay con que mandar un codigo: la clave de YCloud, el numero emisor y la plantilla del codigo. */
    boolean configurado();

    /** El numero en formato internacional ({@code +51987654321}), o vacio si lo escrito no tiene forma de numero de WhatsApp. */
    Optional<NumeroParaCodigo> numero(String texto);

    /** Manda el codigo y espera la respuesta del proveedor. Nunca lanza: {@code false} si el proveedor lo rechazo. */
    boolean enviar(NumeroParaCodigo numero, String codigo);

    /**
     * Lo mismo, sin esperar (en otro hilo): para la recuperacion de la contraseña, donde quien pide no debe poder saber, por lo que tarda la
     * respuesta, si la cuenta tiene un WhatsApp guardado (FR-009).
     */
    void enviarSinEsperar(NumeroParaCodigo numero, String codigo);

    /**
     * @param valor       {@code +51987654321}: lo que se manda al proveedor y lo que cuenta los limites
     * @param enmascarado {@code +51 9•• ••• •21}: lo unico que puede ir a una respuesta o a un registro
     */
    record NumeroParaCodigo(String valor, String enmascarado) {

        /** Nunca imprime el numero completo. */
        @Override
        public String toString() {
            return enmascarado;
        }
    }
}
