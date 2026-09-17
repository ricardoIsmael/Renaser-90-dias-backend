package com.renaser.os.chat.application.ports.in.presencia;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.util.Set;

/**
 * El estado inicial de presencia al abrir una conversacion.
 *
 * <p>Hace falta ademas del empuje en vivo porque una suscripcion solo entrega CAMBIOS: quien
 * abre el chat con la otra persona ya conectada no recibiria nada y veria "desconectado"
 * hasta que el otro se fuera. Se pregunta una vez al abrir y a partir de ahi manda el socket.
 */
public interface ConsultarPresenciaUseCase {

    /** Los participantes de {@code conversacionId} que estan en linea, sin incluir al actor. */
    Set<UserId> enLineaEn(ConversacionId conversacionId, UserId actorId);
}
