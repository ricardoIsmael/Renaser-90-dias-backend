package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Map;

public interface ListarUsuariosDeConversacionPort {

    /**
     * Todos los {@code usuario_id} participantes de una conversacion, sin resolver
     * nombre/avatar/rol — eso lo hace `users.api`, EN LOTE, en el caso de uso que llama a
     * este puerto (nunca una consulta a `users` por participante).
     *
     * <p>Base del directorio de miembros (#27) y del roster del chat GLOBAL (#28): todo
     * usuario activo es participante de GLOBAL por auto-join
     * (V1__baseline_renaser.sql:1293-1295, {@code UsuarioRegistradoChatListener}), asi que
     * esta misma consulta alcanza para ambos casos de uso.
     */
    List<UserId> usuariosDe(ConversacionId conversacionId);

    /**
     * Con quien habla el actor en cada conversacion DIRECTA, en UNA consulta.
     *
     * <p>Existe porque el listado no decia con quien es cada chat: el cliente solo podia adivinar
     * el nombre cuando el ultimo mensaje lo habia mandado el otro. Si lo habias mandado tu, o si
     * la conversacion estaba vacia, la fila decia "Conversacion directa" -- y una bandeja con dos
     * filas iguales no se puede usar.
     *
     * <p>En lote y no por conversacion: el listado trae todas las del actor de una vez, y una
     * consulta por fila seria N+1 en la pantalla que mas se abre.
     *
     * <p>Devuelve SOLO el otro participante de conversaciones de dos. Un grupo tiene su propio
     * nombre y no lo necesita; ademas, mandar el roster entero de cada conversacion en un listado
     * seria repartir mas datos personales de los que la pantalla usa.
     */
    Map<ConversacionId, UserId> otroParticipanteDeDirectas(List<ConversacionId> conversacionIds, UserId actorId);
}
