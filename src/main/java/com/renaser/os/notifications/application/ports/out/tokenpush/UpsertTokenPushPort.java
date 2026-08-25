package com.renaser.os.notifications.application.ports.out.tokenpush;

import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;

public interface UpsertTokenPushPort {

    /** {@code token} es unico globalmente (V1__baseline_renaser.sql:1345): si ya existe una
     * fila para ese token, la re-vincula (usuario/plataforma/actualizadoEn) en vez de duplicar
     * — mismo criterio que el UPSERT de {@code chat/repository.ts:upsertPushToken}. */
    TokenPush upsertPorToken(TokenPush tokenPush);
}
