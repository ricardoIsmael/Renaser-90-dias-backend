package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasDePruebaFinder;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Set;

/** Cuáles cuentas son de prueba de «ver como» (ver {@link CuentasDePruebaFinder}; D-273). */
@Service
class CuentasDePruebaFinderService implements CuentasDePruebaFinder {

    private final LoadUserPort loadUserPort;

    CuentasDePruebaFinderService(LoadUserPort loadUserPort) {
        this.loadUserPort = loadUserPort;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean esCuentaDePrueba(UserId id) {
        return id != null && loadUserPort.cuentasDePruebaEntre(Set.of(id)).contains(id);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UserId> entre(Collection<UserId> ids) {
        return ids == null || ids.isEmpty() ? Set.of() : loadUserPort.cuentasDePruebaEntre(ids);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UserId> todas() {
        return loadUserPort.cuentasDePrueba();
    }
}
