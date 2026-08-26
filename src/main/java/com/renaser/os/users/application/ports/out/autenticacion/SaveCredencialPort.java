package com.renaser.os.users.application.ports.out.autenticacion;

import com.renaser.os.users.domain.model.user.Credencial;
import com.renaser.os.shared.domain.UserId;

public interface SaveCredencialPort {

    /**
     * Escribe solo las dos columnas de credencial de `usuarios`, sin tocar el resto de la fila:
     * el hash nunca viaja por {@code UserJpaEntity}, para que no pueda salir por una respuesta
     * HTTP ni por un log (docs/MODULO_AUTH.md §2.2).
     */
    void guardar(UserId usuarioId, Credencial credencial);
}
