package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasCerradasFinder;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

/** Lo que los demás módulos preguntan para sacar de sus listados a las cuentas cerradas (D-243). */
@Service
class CuentasCerradasFinderService implements CuentasCerradasFinder {

    private final LoadUserPort loadUserPort;

    CuentasCerradasFinderService(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    @Override
    public Set<UserId> cerradasEntre(Collection<UserId> ids) {
        if (ids == null || ids.isEmpty()) {
            return Set.of();
        }
        return loadUserPort.cerradasEntre(ids);
    }
}
