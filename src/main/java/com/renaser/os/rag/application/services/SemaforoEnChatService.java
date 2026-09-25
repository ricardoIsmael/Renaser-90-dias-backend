package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.semaforo.DejarSemaforoEnChatUseCase;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.semaforo.SemaforoEnChat;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

/**
 * El acompañante cuenta en el chat cómo cerró la semana del semáforo (D-168): mismas garantías que la
 * celebración de logros y el aviso de hábito (D-155), con las guardas compartidas en
 * {@link MensajeProactivoDelAcompanante} — id determinístico por persona y semana (una reentrega del
 * outbox no duplica) y nunca detrás de un pedido sin respuesta (D-132).
 *
 * <p><b>Sin chequeo de "llegó tarde"</b>, igual que {@code LogroEnChatService}: "cerraste la semana en
 * verde" sigue siendo cierto aunque se lea el domingo. Lo que sí vigila que el cierre no sea tardío es
 * {@code points}, que solo publica el evento si la semana se cerró dentro del fin de semana.
 *
 * <p>Sin IA, sin transacción propia y sin cuota: la cuota es de lo que la persona escribe.
 */
@Service
public class SemaforoEnChatService implements DejarSemaforoEnChatUseCase {

    /** Prefijo del id determinístico: distinto del de logros y avisos, para que dos usos nunca choquen. */
    static final String PREFIJO_ID = "semaforo-en-chat:";

    private final SemaforoEnChat semaforoEnChat;
    private final MensajeProactivoDelAcompanante mensajeProactivo;

    public SemaforoEnChatService(SemaforoEnChat semaforoEnChat, LoadMensajeRenasiaPort loadMensajePort,
                                 SaveMensajeRenasiaPort saveMensajePort,
                                 LoadConversacionRenasiaPort loadConversacionPort,
                                 SaveConversacionRenasiaPort saveConversacionPort, Clock clock) {
        this.semaforoEnChat = semaforoEnChat;
        this.mensajeProactivo = new MensajeProactivoDelAcompanante(loadMensajePort, saveMensajePort,
                loadConversacionPort, saveConversacionPort, clock);
    }

    @Override
    public boolean dejarEnElChat(SemaforoEnChatCommand command) {
        if (!semaforoEnChat.aplicaA(command.cierre())) {
            return false;
        }
        MensajeRenasiaId id = idDelCierre(command.claveEvento());
        if (!mensajeProactivo.puedeEscribir(id, command.participanteId())) {
            return false;
        }
        Optional<String> texto = semaforoEnChat.redactar(command.cierre());
        if (texto.isEmpty()) {
            return false;
        }
        mensajeProactivo.escribir(id, command.participanteId(), texto.get());
        return true;
    }

    static MensajeRenasiaId idDelCierre(UUID claveEvento) {
        return MensajeProactivoDelAcompanante.idDeterministico(PREFIJO_ID + claveEvento);
    }
}
