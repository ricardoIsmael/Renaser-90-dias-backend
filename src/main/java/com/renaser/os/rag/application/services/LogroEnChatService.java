package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.logro.CelebrarLogroEnChatUseCase;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.logro.LogrosEnChat;
import com.renaser.os.rag.domain.model.logro.TipoLogro;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * El acompanante celebra los logros en el chat: mismas garantias que el aviso de habito (D-155),
 * con las guardas compartidas en {@link MensajeProactivoDelAcompanante} — id deterministico por
 * evento (una reentrega del outbox no duplica) y nunca detras de un pedido sin respuesta (D-132).
 *
 * <p><b>Sin chequeo de "llego tarde"</b>, a diferencia del aviso: el aviso dice una hora que deja de
 * ser verdad, la celebracion no. Un logro celebrado con demora sigue siendo cierto.
 *
 * <p>Sin IA, sin transaccion propia y sin cuota: la cuota es de lo que la persona escribe.
 */
@Service
public class LogroEnChatService implements CelebrarLogroEnChatUseCase {

    /** Prefijo del id deterministico: distinto del de los avisos, para que dos usos nunca choquen. */
    static final String PREFIJO_ID = "logro-en-chat:";

    private final LogrosEnChat logrosEnChat;
    private final MensajeProactivoDelAcompanante mensajeProactivo;

    public LogroEnChatService(LogrosEnChat logrosEnChat, LoadMensajeRenasiaPort loadMensajePort,
                              SaveMensajeRenasiaPort saveMensajePort,
                              LoadConversacionRenasiaPort loadConversacionPort,
                              SaveConversacionRenasiaPort saveConversacionPort, Clock clock) {
        this.logrosEnChat = logrosEnChat;
        this.mensajeProactivo = new MensajeProactivoDelAcompanante(loadMensajePort, saveMensajePort,
                loadConversacionPort, saveConversacionPort, clock);
    }

    @Override
    public boolean celebrarEnElChat(LogroEnChatCommand command) {
        if (!logrosEnChat.aplicaA(command.tipo())) {
            return false;
        }
        MensajeRenasiaId id = idDelLogro(command.tipo(), command.claveEvento());
        if (!mensajeProactivo.puedeEscribir(id, command.participanteId())) {
            return false;
        }
        Optional<String> texto = logrosEnChat.redactar(command.tipo());
        if (texto.isEmpty()) {
            return false;
        }
        mensajeProactivo.escribir(id, command.participanteId(), texto.get());
        return true;
    }

    /** El tipo entra en el nombre: un rachaId y un rocaId iguales no pueden pisarse. */
    static MensajeRenasiaId idDelLogro(TipoLogro tipo, UUID claveEvento) {
        return MensajeProactivoDelAcompanante.idDeterministico(PREFIJO_ID + tipo.name() + ":" + claveEvento);
    }
}
