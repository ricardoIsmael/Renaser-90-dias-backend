package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.ExigirChatPrendidoUseCase;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.shared.application.Interruptores;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

/**
 * Las funcionalidades del chat que dependen del recurso y no de la ruta (spec 002, catálogo): la de cada tipo de
 * conversación y, al mandar media, la de notas de voz ({@code chat.audio}) o fotos ({@code chat.fotos}). El
 * {@code @Funcionalidad("chat")} de los controllers ya cubre el chat entero; esto cubre sus hijas.
 */
@Service
public class InterruptoresDelChat implements ExigirChatPrendidoUseCase {

    static final String NOTAS_DE_VOZ = "chat.audio";
    static final String FOTOS = "chat.fotos";

    private final Interruptores interruptores;
    private final LoadConversacionPort loadConversacionPort;

    public InterruptoresDelChat(Interruptores interruptores, LoadConversacionPort loadConversacionPort) {
        this.interruptores = interruptores;
        this.loadConversacionPort = loadConversacionPort;
    }

    @Override
    public void exigir(ConversacionId conversacionId, UserId actor) {
        loadConversacionPort.porId(conversacionId).ifPresent(conversacion -> exigir(conversacion, actor));
    }

    void exigir(Conversacion conversacion, UserId actor) {
        interruptores.exigirPrendidaPara(claveDe(conversacion.tipo()), actor);
    }

    /** Una nota de voz pide {@code chat.audio}; una foto, un sticker o un video, {@code chat.fotos}. */
    void exigirMedia(String tipoContenido, UserId actor) {
        if (tipoContenido == null) {
            return;
        }
        interruptores.exigirPrendidaPara(tipoContenido.startsWith("audio/") ? NOTAS_DE_VOZ : FOTOS, actor);
    }

    static String claveDe(TipoConversacion tipo) {
        return switch (tipo) {
            case CELULA -> "chat.grupos";
            case GLOBAL -> "chat.global";
            case SOPORTE -> "chat.soporte";
            case DIRECTA -> "chat.directos";
        };
    }
}
