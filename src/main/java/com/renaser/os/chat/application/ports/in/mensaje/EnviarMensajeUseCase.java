package com.renaser.os.chat.application.ports.in.mensaje;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;

public interface EnviarMensajeUseCase {

    Mensaje enviar(EnviarMensajeCommand command);

    /**
     * QUIEN eligio {@code mediaRuta}. Es el dato que faltaba para distinguir compartir una
     * publicacion del Muro de pegar a mano la clave de la foto de otra persona: las dos cosas
     * llegan a {@link #enviar} con una ruta que no es del prefijo de la conversacion, y hasta
     * ahora eran indistinguibles porque lo unico que se miraba era la FORMA de la clave.
     *
     * <p><b>El cliente no puede elegir este valor.</b> {@code EnviarMensajeRequest} no tiene un
     * campo para el y {@code MensajeController} pasa siempre {@link #CLIENTE}; el unico que pone
     * {@link #MURO_COMPARTIDO} es {@code CompartirPublicacionService}, que es codigo de servidor.
     */
    enum OrigenMedia {

        /**
         * La ruta viaja en el cuerpo de la peticion. Solo se acepta si apunta al prefijo de la
         * propia conversacion: cualquier otra clave del unico bucket fisico queda afuera.
         */
        CLIENTE,

        /**
         * La ruta la derivo el SERVIDOR de una publicacion del Muro, y esa publicacion ya cruzo
         * la puerta de visibilidad de {@code PublicacionMuroFinder.paraCompartir} (que filtra las
         * {@code oculta}, o sea lo que deja "borrar mi publicacion" y la moderacion).
         *
         * <p>No se vuelve a exigir un prefijo sobre la clave, y no es un descuido: la ruta de una
         * publicacion ya viene acotada de su propia creacion, en las DOS puertas que existen —
         * {@code MediaItemRequest.exigirClaveDelMuro} obliga a {@code muro/} en el POST del Muro,
         * y {@code PublicacionMuroService.exigirRutaDelAutor} obliga a
         * {@code rocas/<autorId>/} cuando la publicacion la crea `rocks` al completar una Roca.
         * Exigir {@code muro/} aca rechazaria justamente a las segundas, que son publicaciones
         * legitimas y compartibles.
         */
        MURO_COMPARTIDO
    }

    record EnviarMensajeCommand(@NotNull UserId actorId, @NotNull ConversacionId conversacionId,
                                 @NotNull TipoMensaje tipo, String texto, String mediaBucket, String mediaRuta,
                                 String mediaMime, Integer mediaBytes, Short mediaDuracionS,
                                 MensajeId respuestaAId, @NotNull OrigenMedia origenMedia) {

        public EnviarMensajeCommand {
            SelfValidating.validateConstructorArgs(EnviarMensajeCommand.class, actorId, conversacionId, tipo, texto,
                    mediaBucket, mediaRuta, mediaMime, mediaBytes, mediaDuracionS, respuestaAId, origenMedia);
        }
    }
}
