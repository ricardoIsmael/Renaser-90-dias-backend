package com.renaser.os.rag.application.ports.in.voz;

import com.renaser.os.rag.domain.model.conversacion.EventoDeVozEnVivo;
import com.renaser.os.shared.domain.UserId;

/**
 * Conversar con el acompanante por voz en tiempo real (D-162, contrato en §5.ter de
 * {@code docs/arquitectura/PROPUESTA_GEMINI_LIVE.md}).
 *
 * <p>El transporte (hoy un WebSocket) implementa {@link SalidaDeVozEnVivo} y recibe una
 * {@link ConversacionEnVivo} por la que manda el audio de la persona. Todo lo demas —cuota,
 * herramientas, propuestas, historial— lo decide el caso de uso.
 */
public interface ConversarEnVivoUseCase {

    /** Cuenta activa con permiso {@code USE_APP}. Se pregunta antes de aceptar la conexion. */
    boolean puedeConversar(UserId actorId);

    /**
     * Abre la conversacion. No lanza por motivos de negocio (sin cuota, apagado, proveedor caido):
     * los avisa por {@code salida} con un evento y la cierra, y devuelve una conversacion que
     * ignora lo que le manden.
     */
    ConversacionEnVivo iniciar(UserId actorId, SalidaDeVozEnVivo salida);

    /** Lo que la app le manda al backend. */
    interface ConversacionEnVivo {

        /** PCM de 16 bits, mono, 16 kHz. */
        void recibirAudio(byte[] pcm16kHz);

        /** La persona cerro el orbe o se corto la conexion. Idempotente. */
        void terminar();
    }

    /** Lo que el backend le manda a la app. */
    interface SalidaDeVozEnVivo {

        /** PCM de 16 bits, mono, 16 kHz. */
        void audio(byte[] pcm16kHz);

        void evento(EventoDeVozEnVivo evento);

        /** Cerrar la conexion; se llama una sola vez y despues de ella no llega nada mas. */
        void cerrar(MotivoDeCierre motivo);
    }

    enum MotivoDeCierre {
        /** La persona termino, o el proveedor cerro sin error. */
        NORMAL,
        /** Se acabaron los minutos del dia ({@code cuotaAgotada}). */
        CUOTA_AGOTADA,
        /** Voz en vivo apagada o proveedor que no abre: la app usa el flujo anterior. */
        NO_DISPONIBLE,
        /** Algo fallo a mitad de camino; ya se mando un {@code error}. */
        ERROR
    }
}
