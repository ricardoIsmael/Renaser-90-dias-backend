package com.renaser.os.chat.application.ports.out.lectura;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;

import java.util.List;

/**
 * Los participantes de UNA conversación con su marca de lectura ({@code ultimo_leido_en}), en una sola
 * consulta: la base de la doble marca (D-208). Nunca se pide para la comunidad (GLOBAL), que tiene a
 * toda la generación adentro y no lleva ✓✓.
 */
@FunctionalInterface
public interface MarcasDeLecturaPort {

    List<Participante> participantesDe(ConversacionId conversacionId);
}
