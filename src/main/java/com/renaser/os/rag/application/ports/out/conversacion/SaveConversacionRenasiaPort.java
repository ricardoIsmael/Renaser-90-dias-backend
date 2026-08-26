package com.renaser.os.rag.application.ports.out.conversacion;

import com.renaser.os.rag.domain.model.conversacion.ConversacionRenasia;

public interface SaveConversacionRenasiaPort {

    ConversacionRenasia save(ConversacionRenasia conversacion);
}
