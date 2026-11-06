package com.renaser.os.community.infrastructure.adapter.out.persistence.usuario;

import com.renaser.os.community.application.ports.out.sobrenombre.SobrenombresPort;
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
    private final SobrenombresPort sobrenombres;

    ConsultarPerfilUsuarioPersistenceAdapter(UserSummaryFinder userSummaryFinder, SobrenombresPort sobrenombres) {
        this.userSummaryFinder = userSummaryFinder;
        this.sobrenombres = sobrenombres;
    }

    @Override
    public Optional<PerfilUsuario> porId(UserId id) {
        return userSummaryFinder.findById(id).map(r -> aPerfil(r, sobrenombres.sobrenombresDe(java.util.List.of(id)).get(id)));
    }

    /** Sigue siendo una sola pasada: el contrato publico tiene su propia version en lote. */
    @Override
    public Map<UserId, PerfilUsuario> porIds(Collection<UserId> ids) {
        Map<UserId, String> apodos = sobrenombres.sobrenombresDe(ids);
        return userSummaryFinder.findByIds(ids).values().stream()
                .collect(Collectors.toMap(UserSummary::id, r -> aPerfil(r, apodos.get(r.id()))));
    }

    private static PerfilUsuario aPerfil(UserSummary resumen, String sobrenombre) {
        return new PerfilUsuario(resumen.id(), resumen.fullName(), resumen.avatarUrl(), sobrenombre);
    }
}
