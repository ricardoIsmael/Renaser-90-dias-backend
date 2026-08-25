package com.renaser.os.notifications.application.ports.out.tokenpush;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface LoadTokenPushPort {

    /** Todos los tokens Expo registrados para un usuario (puede tener mas de un dispositivo). */
    List<String> tokensDe(UserId usuarioId);
}
