package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.CuentasDePruebaFinder;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Set;

/**
 * Sin la marca de la fase 2 (R-1, spec 005), ninguna cuenta es de prueba. Es una implementación honesta y no un
 * filtro que invente un criterio: al integrar la fase 2 este servicio pasa a leer {@code es_cuenta_de_prueba}.
 */
@Service
class CuentasDePruebaFinderService implements CuentasDePruebaFinder {

    @Override
    public Set<UserId> dePruebaEntre(Collection<UserId> ids) {
        return Set.of();
    }
}
