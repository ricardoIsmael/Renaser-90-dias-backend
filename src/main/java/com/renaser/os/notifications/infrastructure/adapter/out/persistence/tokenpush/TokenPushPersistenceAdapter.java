package com.renaser.os.notifications.infrastructure.adapter.out.persistence.tokenpush;

import com.renaser.os.notifications.application.ports.out.tokenpush.LoadTokenPushPort;
import com.renaser.os.notifications.application.ports.out.tokenpush.UpsertTokenPushPort;
import com.renaser.os.notifications.domain.model.tokenpush.TokenPush;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
class TokenPushPersistenceAdapter implements UpsertTokenPushPort, LoadTokenPushPort {

    private final SpringDataTokenPushRepository repository;
    private final TokenPushPersistenceMapper mapper;

    TokenPushPersistenceAdapter(SpringDataTokenPushRepository repository, TokenPushPersistenceMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    public TokenPush upsertPorToken(TokenPush tokenPush) {
        // token es UNIQUE en el esquema: si ya hay fila para este token, se conserva SU id
        // (nunca se duplica) y solo se actualiza usuario/plataforma/fecha — mismo criterio que
        // el UPSERT de chat/repository.ts:upsertPushToken del repo viejo.
        TokenPushJpaEntity entidad = repository.findByToken(tokenPush.token())
                .map(existente -> {
                    existente.setUsuarioId(tokenPush.usuarioId().value());
                    existente.setPlataforma(mapper.toJpaPlataforma(tokenPush.plataforma()));
                    existente.setActualizadoEn(tokenPush.actualizadoEn());
                    return existente;
                })
                .orElseGet(() -> mapper.toEntity(tokenPush));

        return mapper.toDomain(repository.save(entidad));
    }

    @Override
    public List<String> tokensDe(UserId usuarioId) {
        return repository.findByUsuarioId(usuarioId.value()).stream().map(TokenPushJpaEntity::getToken).toList();
    }
}
