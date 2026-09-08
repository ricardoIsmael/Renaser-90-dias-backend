package com.renaser.os.notifications.application.ports.out.tokenpush;

import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

public interface LoadTokenPushPort {

    /** Todas las suscripciones registradas para un usuario (Android/iOS/Web). */
    List<TokenPush> tokensDe(UserId usuarioId);
}
