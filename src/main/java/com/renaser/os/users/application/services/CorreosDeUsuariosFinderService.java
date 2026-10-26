package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CorreosDeUsuariosFinder;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.user.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

/** El correo de varias cuentas, en lote (D-271): una lectura de {@code LoadUserPort.byIds}. */
@Service
class CorreosDeUsuariosFinderService implements CorreosDeUsuariosFinder {

    private final LoadUserPort loadUserPort;

    CorreosDeUsuariosFinderService(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UserId, String> correosDe(Collection<UserId> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return loadUserPort.byIds(ids).stream()
                .collect(Collectors.toUnmodifiableMap(User::id, usuario -> usuario.email().value(), (a, b) -> a));
    }
}
