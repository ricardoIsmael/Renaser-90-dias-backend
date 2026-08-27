package com.renaser.os.users.infrastructure.adapter.in.rest.user;

import com.renaser.os.users.application.ports.in.user.SolicitarUrlAvatarUseCase.UrlAvatar;

public record UrlAvatarResponse(String url, String bucket, String ruta) {

    public static UrlAvatarResponse from(UrlAvatar url) {
        return new UrlAvatarResponse(url.url().toString(), url.bucket(), url.ruta());
    }
}
