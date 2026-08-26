package com.renaser.os.rag.application.ports.out.conversacion;

import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;

public interface SaveMensajeRenasiaPort {

    MensajeRenasia save(MensajeRenasia mensaje);
}
