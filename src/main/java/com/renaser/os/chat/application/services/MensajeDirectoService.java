package com.renaser.os.chat.application.services;

import com.renaser.os.chat.api.MensajeDirectoPort;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.CrearConversacionDirectaUseCase.CrearConversacionDirectaCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.EnviarMensajeCommand;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeUseCase.OrigenMedia;
import com.renaser.os.chat.application.ports.in.mensaje.MensajeEnriquecido;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.mensaje.TipoMensaje;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * El mensaje de texto de una persona a otra en su chat directo, pedido por otro módulo (spec 056, FR-019, FR-025 y FR-029).
 *
 * <p>No agrega reglas: es la composición de dos casos de uso que ya existen. {@link CrearConversacionDirectaUseCase} busca o crea la conversación de dos
 * (con sus chequeos de cuenta activa, formación en común e interruptor de chats directos) y {@link EnviarMensajeUseCase} guarda el mensaje (con la
 * pertenencia a la conversación, el largo máximo y el aviso que dispara el push). Que el guardado sea el de siempre es lo que evita un segundo camino
 * hacia el chat que se salte los interruptores de Soporte TI.
 *
 * <p>Corre dentro de la transacción de quien llama, así que si esa se deshace el mensaje también (dos pedidos a la vez con la misma clave no dejan dos
 * mensajes), y un rechazo del chat deshace la de quien llama con su motivo.
 */
@Service
public class MensajeDirectoService implements MensajeDirectoPort {

    private final CrearConversacionDirectaUseCase conversaciones;
    private final EnviarMensajeUseCase mensajes;

    public MensajeDirectoService(CrearConversacionDirectaUseCase conversaciones, EnviarMensajeUseCase mensajes) {
        this.conversaciones = conversaciones;
        this.mensajes = mensajes;
    }

    @Override
    @Transactional
    public MensajeDirecto enviar(UserId remitente, UserId destinatario, String texto) {
        if (texto == null || texto.isBlank()) {
            throw new IllegalArgumentException("Escribe el mensaje: no puede quedar vacío");
        }
        Conversacion directa = conversaciones.obtenerOCrear(new CrearConversacionDirectaCommand(remitente, destinatario));
        MensajeEnriquecido guardado = mensajes.enviar(new EnviarMensajeCommand(remitente, directa.id(), TipoMensaje.TEXTO, texto, null, null, null, null,
                null, null, OrigenMedia.CLIENTE));
        return new MensajeDirecto(directa.id().value(), guardado.mensaje().id().value());
    }
}
