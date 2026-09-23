package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.out.conversacion.LoadConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveConversacionRenasiaPort;
import com.renaser.os.rag.application.ports.out.conversacion.SaveMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.rag.domain.model.conversacion.RolMensaje;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

/**
 * Lo que tienen en comun todos los mensajes que el acompanante deja SIN que la persona le haya
 * escrito: los avisos de habito (D-155) y la celebracion de logros. Extraido del primero cuando
 * aparecio el segundo, para que las dos guardas de abajo vivan en un solo lugar.
 *
 * <p><b>Idempotencia sin columna nueva.</b> El id del mensaje se DERIVA de una clave del evento
 * ({@link #idDeterministico}); antes de guardar se pregunta {@link LoadMensajeRenasiaPort#existe},
 * porque un {@code save} con un id existente seria un UPDATE silencioso que pisa el mensaje ya
 * mostrado. Si dos entregas corren a la vez, la clave primaria es la ultima guarda: la segunda
 * falla, el outbox la reintenta y en el reintento ya existe.
 *
 * <p><b>No rompe la memoria de D-132.</b> {@code soloTurnosRespondidos} deja afuera un mensaje de
 * la persona que no tuvo respuesta, y decide "tuvo respuesta" mirando si lo SIGUIENTE es del
 * asistente. Un mensaje proactivo escrito justo despues de un pedido que fallo lo haria pasar por
 * respondido, y el pedido viejo volveria a la memoria del modelo: exactamente el incidente de
 * D-132. Por eso, si lo ultimo del chat es un mensaje de la persona sin respuesta, no se escribe.
 *
 * <p>No es un bean: cada servicio la arma con sus propios puertos, asi la firma publica de
 * {@code AvisoHabitoEnChatService} quedo igual que antes de extraerla. Sin transaccion propia ni
 * llamadas externas (regla 01, C-1): cada puerto corre en su transaccion corta.
 */
final class MensajeProactivoDelAcompanante {

    private static final AgenteConversacional ACOMPANANTE = AgenteConversacional.COMPANION;

    private final LoadMensajeRenasiaPort loadMensajePort;
    private final SaveMensajeRenasiaPort saveMensajePort;
    private final LoadConversacionRenasiaPort loadConversacionPort;
    private final SaveConversacionRenasiaPort saveConversacionPort;
    private final Clock clock;

    MensajeProactivoDelAcompanante(LoadMensajeRenasiaPort loadMensajePort, SaveMensajeRenasiaPort saveMensajePort,
                                   LoadConversacionRenasiaPort loadConversacionPort,
                                   SaveConversacionRenasiaPort saveConversacionPort, Clock clock) {
        this.loadMensajePort = loadMensajePort;
        this.saveMensajePort = saveMensajePort;
        this.loadConversacionPort = loadConversacionPort;
        this.saveConversacionPort = saveConversacionPort;
        this.clock = clock;
    }

    /**
     * {@code nameUUIDFromBytes}: funcion pura del nombre, la misma entrega tras entrega. Quien llama
     * antepone un prefijo propio para que dos usos distintos de la misma clave no choquen.
     */
    static MensajeRenasiaId idDeterministico(String nombre) {
        return MensajeRenasiaId.of(UUID.nameUUIDFromBytes(nombre.getBytes(StandardCharsets.UTF_8)));
    }

    /** Ni ya escrito, ni detras de un pedido sin respuesta (ver el javadoc de la clase). */
    boolean puedeEscribir(MensajeRenasiaId id, UserId participanteId) {
        return !loadMensajePort.existe(id) && !loUltimoEsUnPedidoSinRespuesta(participanteId);
    }

    /**
     * La conversacion puede no existir todavia: alguien que nunca abrio el chat igual recibe el
     * mensaje. Es el mismo buscar-o-crear de {@code ConversacionRenasiaService}, repetido y no
     * extraido a proposito: son dos lineas sobre dos puertos que ya existen, y extraerlas obligaba a
     * tocar ese servicio sin ganar nada.
     */
    void escribir(MensajeRenasiaId id, UserId participanteId, String texto) {
        if (loadConversacionPort.porUsuarioId(participanteId).isEmpty()) {
            saveConversacionPort.save(ConversacionRenasia.iniciar(participanteId, clock.now()));
        }
        saveMensajePort.save(MensajeRenasia.escribirDeAsistente(id, participanteId, ACOMPANANTE, texto, List.of(),
                clock.now()));
    }

    private boolean loUltimoEsUnPedidoSinRespuesta(UserId participanteId) {
        List<MensajeRenasia> ultimo = loadMensajePort.pagina(participanteId, ACOMPANANTE, null, 1);
        return !ultimo.isEmpty() && ultimo.getFirst().rol() == RolMensaje.USUARIO;
    }
}
