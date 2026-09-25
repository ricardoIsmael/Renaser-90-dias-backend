package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.aviso.DejarAvisoHabitoEnChatUseCase;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.domain.model.aviso.AvisosEnChat;
import com.renaser.os.rag.domain.model.aviso.AvisosEnChat.DatosDelAviso;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.shared.domain.Clock;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

/**
 * El acompanante escribe primero (fase 5, §5.1 de la propuesta): cuando {@code habits} publica un
 * aviso de habito, ademas del push de {@code notifications} queda un mensaje del acompanante en la
 * conversacion. Plantilla y datos reales; ninguna llamada a la IA.
 *
 * <p><b>Idempotencia sin columna nueva.</b> {@code habits} republica el mismo aviso en cada barrido
 * de 5 minutos mientras dura su franja, y el outbox puede reentregar. El id del mensaje se DERIVA
 * de {@code claveEvento} (ya deterministica por registro y tipo, ver {@code TipoAvisoHabito}): la
 * segunda vez {@link LoadMensajeRenasiaPort#existe} lo encuentra y no se escribe nada. El detalle
 * (y la guarda de D-132 de abajo) vive en {@link MensajeProactivoDelAcompanante}, compartida con
 * la celebracion de logros.
 *
 * <p><b>La hora se deriva del evento, no se recalcula.</b> El evento trae el instante del calculo
 * y los minutos que faltaban, redondeados hacia arriba por {@code habits}; sumarlos y truncar al
 * minuto devuelve exactamente el inicio o el plazo (que siempre caen en minuto justo), y se dice
 * en la zona del aprendiz — la MISMA con la que {@code habits} lo calculo
 * ({@link ConsultarAgendaHabitosPort#zonaDe}), nunca la del servidor (regla 02 §1).
 *
 * <p><b>Un aviso que llega tarde no se escribe.</b> Si el outbox lo entrega cuando el momento ya
 * paso, el texto seria falso ("vence a las 21:30" a las 22:00). Mismo criterio que
 * {@code CalculadoraAvisosHabito}: es preferible no avisar a avisar algo falso.
 *
 * <p><b>No rompe la memoria de D-132.</b> {@code soloTurnosRespondidos} deja afuera un mensaje de
 * la persona que no tuvo respuesta, y decide "tuvo respuesta" mirando si lo SIGUIENTE es del
 * asistente. Un aviso escrito justo despues de un pedido que fallo lo haria pasar por respondido,
 * y el pedido viejo volveria a la memoria del modelo: exactamente el incidente de D-132. Por eso,
 * si lo ultimo del chat es un mensaje de la persona sin respuesta, este aviso NO se escribe
 * ({@link MensajeProactivoDelAcompanante#puedeEscribir}). No se pierde nada que no se recupere: si el turno
 * estaba en curso, el barrido siguiente republica el aviso y, ya con la respuesta guardada, se
 * escribe; el push de {@code notifications} sale igual en cualquier caso.
 *
 * <p><b>Sin transaccion propia y sin cuota.</b> No hay ninguna llamada externa (regla 01, C-1);
 * cada puerto corre en su transaccion corta. No toca {@code ControlCuotaRenasiaPort}: la cuota es
 * de lo que la persona escribe, y esto no lo escribio ella.
 */
@Service
public class AvisoHabitoEnChatService implements DejarAvisoHabitoEnChatUseCase {

    /** Prefijo del id deterministico: separa este uso de la clave de cualquier otro. */
    static final String PREFIJO_ID = "aviso-habito-en-chat:";

    private final AvisosEnChat avisosEnChat;
    private final MensajeProactivoDelAcompanante mensajeProactivo;
    private final ConsultarAgendaHabitosPort agendaPort;
    private final Clock clock;

    public AvisoHabitoEnChatService(AvisosEnChat avisosEnChat, LoadMensajeRenasiaPort loadMensajePort,
                                     SaveMensajeRenasiaPort saveMensajePort,
                                     LoadConversacionRenasiaPort loadConversacionPort,
                                     SaveConversacionRenasiaPort saveConversacionPort,
                                     ConsultarAgendaHabitosPort agendaPort, Clock clock) {
        this.avisosEnChat = avisosEnChat;
        this.mensajeProactivo = new MensajeProactivoDelAcompanante(loadMensajePort, saveMensajePort,
                loadConversacionPort, saveConversacionPort, clock);
        this.agendaPort = agendaPort;
        this.clock = clock;
    }

    @Override
    public boolean dejarEnElChat(AvisoHabitoEnChatCommand command) {
        if (!avisosEnChat.aplicaA(command.tipoAviso())) {
            return false;
        }
        Instant momento = momentoAvisado(command);
        MensajeRenasiaId id = idDelAviso(command.claveEvento());
        if (!clock.now().isBefore(momento) || !mensajeProactivo.puedeEscribir(id, command.participanteId())) {
            return false;
        }
        Optional<String> texto = avisosEnChat.redactar(command.tipoAviso(), datosDe(command, momento));
        if (texto.isEmpty()) {
            return false;
        }
        mensajeProactivo.escribir(id, command.participanteId(), texto.get());
        return true;
    }

    /** Ver el javadoc de la clase: truncar al minuto devuelve el inicio o el plazo exactos. */
    private static Instant momentoAvisado(AvisoHabitoEnChatCommand command) {
        return command.calculadoEn().plus(Duration.ofMinutes(command.minutosQueFaltan()))
                .truncatedTo(ChronoUnit.MINUTES);
    }

    /** Mismos bytes que antes de la extraccion: los ids de avisos ya escritos no cambian. */
    static MensajeRenasiaId idDelAviso(UUID claveEvento) {
        return MensajeProactivoDelAcompanante.idDeterministico(PREFIJO_ID + claveEvento);
    }

    private DatosDelAviso datosDe(AvisoHabitoEnChatCommand command, Instant momento) {
        LocalTime hora = momento.atZone(agendaPort.zonaDe(command.participanteId())).toLocalTime();
        return new DatosDelAviso(command.tituloHabito(), hora, command.puntosEnJuego(), command.minutosQueFaltan());
    }
}
