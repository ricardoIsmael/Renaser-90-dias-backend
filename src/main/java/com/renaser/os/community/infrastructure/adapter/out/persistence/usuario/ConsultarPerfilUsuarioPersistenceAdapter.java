package com.renaser.os.community.infrastructure.adapter.out.persistence.usuario;

import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Delega en el contrato publico de `users` (D-41). La query propia sobre `usuarios` que
 * habia aca existia solo porque {@code UserSummary} no exponia `avatarUrl`; ahora si lo
 * hace, y el pedido que estaba anotado en `docs/MODULO_COMMUNITY.md` sec. 4 queda cerrado.
 */
@Component
class ConsultarPerfilUsuarioPersistenceAdapter implements ConsultarPerfilUsuarioPort {

    private final UserSummaryFinder userSummaryFinder;

    ConsultarPerfilUsuarioPersistenceAdapter(UserSummaryFinder userSummaryFinder) {
        this.userSummaryFinder = userSummaryFinder;
    }

    @Override
    public Optional<PerfilUsuario> porId(UserId id) {
        return userSummaryFinder.findById(id).map(ConsultarPerfilUsuarioPersistenceAdapter::aPerfil);
    }

    /** Sigue siendo una sola pasada: el contrato publico tiene su propia version en lote. */
    @Override
    public Map<UserId, PerfilUsuario> porIds(Collection<UserId> ids) {
        return userSummaryFinder.findByIds(ids).values().stream()
                .collect(Collectors.toMap(UserSummary::id, ConsultarPerfilUsuarioPersistenceAdapter::aPerfil));
    }

    private static PerfilUsuario aPerfil(UserSummary resumen) {
        return new PerfilUsuario(resumen.id(), resumen.fullName(), resumen.avatarUrl());
    }
}
