package com.renaser.os.chat.application.ports.out.participante;

import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface ConversacionesDeUsuarioPort {

    /**
     * Todas las conversaciones en las que participa un usuario.
     *
     * <p>Lo necesita el aviso de presencia: cuando alguien abre o cierra su socket hay que
     * empujar el cambio a las conversaciones donde ese alguien aparece, que son exactamente
     * las pantallas donde otro lo esta mirando. Sin esto habria que elegir entre no avisar a
     * nadie o abrir un canal propio de presencia con su propia autorizacion — y la de las
     * conversaciones ya esta escrita y auditada.
     */
    List<ConversacionId> conversacionesDe(UserId usuarioId);
}
