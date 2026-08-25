package com.renaser.os.chat.application.ports.out.mensaje;

import com.renaser.os.chat.domain.model.mensaje.Mensaje;

public interface SaveMensajePort {

    Mensaje save(Mensaje mensaje);
}
