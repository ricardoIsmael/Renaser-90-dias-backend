package com.renaser.os.rag.domain.model.conversacion;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Lo que el backend le cuenta a la app durante una conversacion por voz en vivo (D-162), ademas del
 * audio. Es el contrato de §5.ter de {@code docs/arquitectura/PROPUESTA_GEMINI_LIVE.md}: cada
 * variante es un {@code tipo} del JSON que viaja por el WebSocket.
 *
 * <p>Sellada por el mismo motivo que {@link EventoRenasia}: agregar un evento sin que el traductor
 * a JSON lo contemple no compila.
 */
public sealed interface EventoDeVozEnVivo {

    /** La sesion con el modelo quedo abierta; {@code segundosRestantes} es la cuota del dia. */
    record Listo(long segundosRestantes) implements EventoDeVozEnVivo {
    }

    /** Un pedazo de la transcripcion de lo que dijo la persona. La app los va sumando. */
    record Oido(String texto) implements EventoDeVozEnVivo {
        public Oido {
            Objects.requireNonNull(texto, "texto es obligatorio");
        }
    }

    /** Un pedazo de la transcripcion de lo que dice el acompanante, a la par del audio. */
    record Dicho(String texto) implements EventoDeVozEnVivo {
        public Dicho {
            Objects.requireNonNull(texto, "texto es obligatorio");
        }
    }

    /** La persona hablo encima: la app corta lo que esta sonando. */
    record Interrumpido() implements EventoDeVozEnVivo {
    }

    /** El acompanante termino de responder. */
    record TurnoCompleto() implements EventoDeVozEnVivo {
    }

    /** Igual que el evento {@code propuesta} del SSE del chat (D-153): se confirma con el boton. */
    record Propuesta(UUID id, String resumen, Instant venceEn) implements EventoDeVozEnVivo {
        public Propuesta {
            Objects.requireNonNull(id, "id es obligatorio");
            Objects.requireNonNull(resumen, "resumen es obligatorio");
            Objects.requireNonNull(venceEn, "venceEn es obligatorio");
        }
    }

    /** Igual que el evento {@code evidencia} del SSE del chat (D-171): la tarjeta de la camara. */
    record Evidencia(UUID registroId, String titulo, Instant venceEn) implements EventoDeVozEnVivo {
        public Evidencia {
            Objects.requireNonNull(registroId, "registroId es obligatorio");
            Objects.requireNonNull(titulo, "titulo es obligatorio");
            Objects.requireNonNull(venceEn, "venceEn es obligatorio");
        }
    }

    /** Se acabaron los minutos del dia; la app vuelve al flujo anterior. Despues se cierra. */
    record CuotaAgotada() implements EventoDeVozEnVivo {
    }

    /** Texto apto para mostrar. Despues se cierra. */
    record Error(String valor) implements EventoDeVozEnVivo {
        public Error {
            Objects.requireNonNull(valor, "valor es obligatorio");
        }
    }
}
