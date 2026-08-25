package com.renaser.os.notifications.infrastructure.adapter.out.persistence.tokenpush;

import com.renaser.os.notifications.domain.model.tokenpush.PlataformaPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPushId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class TokenPushPersistenceMapper {

    TokenPush toDomain(TokenPushJpaEntity e) {
        return TokenPush.rehydrate(TokenPushId.of(e.getId()), UserId.of(e.getUsuarioId()), e.getToken(),
                toDomainPlataforma(e.getPlataforma()), e.getCreadoEn(), e.getActualizadoEn());
    }

    TokenPushJpaEntity toEntity(TokenPush t) {
        return new TokenPushJpaEntity(t.id().value(), t.usuarioId().value(), t.token(),
                toJpaPlataforma(t.plataforma()), t.creadoEn(), t.actualizadoEn());
    }

    PlataformaPushJpa toJpaPlataforma(PlataformaPush plataforma) {
        if (plataforma == null) {
            return null;
        }
        return switch (plataforma) {
            case IOS -> PlataformaPushJpa.IOS;
            case ANDROID -> PlataformaPushJpa.ANDROID;
        };
    }

    private PlataformaPush toDomainPlataforma(PlataformaPushJpa jpa) {
        if (jpa == null) {
            return null;
        }
        return switch (jpa) {
            case IOS -> PlataformaPush.IOS;
            case ANDROID -> PlataformaPush.ANDROID;
        };
    }
}
