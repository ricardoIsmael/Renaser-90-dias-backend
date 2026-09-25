package com.renaser.os.rag.application.ports.out.memoria;

import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.memoria.Compactacion;
import com.renaser.os.rag.domain.model.memoria.MemoriaDeRenasia;

import java.util.List;
import java.util.Objects;

/**
 * El modelo que comprime una conversacion en memoria (D-167): lee lo que el acompanante ya sabia y
 * los mensajes que salen de la ventana de los ultimos 10, y devuelve el resumen nuevo y la lista
 * COMPLETA de recuerdos que deben quedar (los que siguen siendo ciertos y los nuevos).
 *
 * <p>Es un puerto de IA: nunca se llama dentro de una transaccion (C-1). Lo que devuelve se sanea
 * en el dominio antes de guardarse ({@code Compactacion.saneados()}).
 */
public interface CompactarConversacionPort {

    /** @throws RuntimeException si el modelo falla o devuelve algo ilegible: se reintenta en otro turno */
    Compactacion compactar(Entrada entrada);

    /** @param mensajes en orden cronologico, del mas viejo al mas nuevo */
    record Entrada(MemoriaDeRenasia actual, List<MensajeRenasia> mensajes) {

        public Entrada {
            Objects.requireNonNull(actual, "actual es obligatoria");
            mensajes = List.copyOf(Objects.requireNonNull(mensajes, "mensajes es obligatorio"));
        }
    }
}
