package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

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
}
