package com.renaser.os.notifications.infrastructure.adapter.in.rest.tokenpush;

import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;

public record TokenPushResponse(String id) {

    public static TokenPushResponse from(TokenPush tokenPush) {
        return new TokenPushResponse(tokenPush.id().toString());
    }
}
