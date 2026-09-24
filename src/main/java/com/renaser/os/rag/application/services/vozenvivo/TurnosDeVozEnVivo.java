package com.renaser.os.rag.application.services.vozenvivo;

import com.renaser.os.rag.application.ports.in.seguridad.RevisarPatronDeMalestarUseCase;
import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.application.services.ConversacionRenasiaService;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/**
 * Guarda en {@code mensajes_renasia} lo que se hablo por voz (decision 3 del dueno, D-162): solo el
 * texto, con el agente {@code COMPANION}, en la misma conversacion que el chat escrito. El audio no
 * se guarda nunca.
 *
 * <p><b>Revisa el malestar igual que el chat.</b> Despues de guardar lo que dijo la persona corre
 * {@link RevisarPatronDeMalestarUseCase}, como {@code ConversacionRenasiaService}: el aviso a quien
 * puede actuar sale igual, y si hay texto de apoyo queda al final del mensaje del acompanante y se
 * devuelve para que la app lo muestre. Best-effort: si falla, el turno se guarda igual.
 *
 * <p>Sin {@code @Transactional}: cada guardado es su propia transaccion corta y ninguna llamada al
 * modelo pasa por aca (C-1).
 */
@Service
public class TurnosDeVozEnVivo {

    private static final Logger log = LoggerFactory.getLogger(TurnosDeVozEnVivo.class);

    private final LoadConversacionRenasiaPort loadConversacionPort;
    private final SaveConversacionRenasiaPort saveConversacionPort;
    private final SaveMensajeRenasiaPort saveMensajePort;
    private final RevisarPatronDeMalestarUseCase revisarPatronDeMalestar;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public TurnosDeVozEnVivo(LoadConversacionRenasiaPort loadConversacionPort,
                             SaveConversacionRenasiaPort saveConversacionPort, SaveMensajeRenasiaPort saveMensajePort,
                             RevisarPatronDeMalestarUseCase revisarPatronDeMalestar, Clock clock,
                             IdGenerator idGenerator) {
        this.loadConversacionPort = loadConversacionPort;
        this.saveConversacionPort = saveConversacionPort;
        this.saveMensajePort = saveMensajePort;
        this.revisarPatronDeMalestar = revisarPatronDeMalestar;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /** Los mensajes cuelgan de la conversacion 1:1 (clave foranea): tiene que existir antes. */
    public void asegurarConversacion(UserId actorId) {
        if (loadConversacionPort.porUsuarioId(actorId).isEmpty()) {
            saveConversacionPort.save(ConversacionRenasia.iniciar(actorId, clock.now()));
        }
    }

    /**
     * Guarda el turno y devuelve el texto de apoyo que hay que mostrar ({@code ""} si no hay).
     * Una parte vacia no se guarda: el dominio no admite mensajes sin contenido.
     */
    String guardar(UserId actorId, TurnoDeVoz turno) {
        Instant inicio = turno.inicio() == null ? clock.now() : turno.inicio();
        String apoyo = "";
        if (!turno.oido().isBlank()) {
            saveMensajePort.save(MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(idGenerator.newId()), actorId,
                    AgenteConversacional.COMPANION, turno.oido(), inicio));
            apoyo = textoDeApoyo(actorId, turno.oido());
        }
        String respuesta = apoyo.isBlank() ? turno.respuesta()
                : (turno.respuesta() + ConversacionRenasiaService.SEPARACION_DEL_APOYO + apoyo).strip();
        if (!respuesta.isBlank()) {
            // Nunca antes que la pregunta: el historial se ordena por fecha.
            Instant fin = clock.now().isAfter(inicio) ? clock.now() : inicio.plusMillis(1);
            saveMensajePort.save(MensajeRenasia.escribirDeAsistente(MensajeRenasiaId.of(idGenerator.newId()),
                    actorId, AgenteConversacional.COMPANION, respuesta, List.of(), fin));
        }
        return apoyo;
    }

    private String textoDeApoyo(UserId actorId, String oido) {
        try {
            return revisarPatronDeMalestar.revisar(actorId, oido).orElse("");
        } catch (RuntimeException e) {
            log.warn("No se pudo revisar la repeticion de expresiones de malestar en la voz en vivo ({}); sigue normal",
                    e.getClass().getSimpleName());
            return "";
        }
    }
}
