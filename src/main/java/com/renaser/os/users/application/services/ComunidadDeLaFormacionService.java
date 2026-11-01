package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.users.api.ComunidadDeLaFormacionFinder;
import com.renaser.os.users.application.ports.in.formacion.ConfigurarComunidadDeLaFormacionUseCase;
import com.renaser.os.users.application.ports.out.formacion.TiposDeComunidadPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Los tipos de comunidad de cada formación (spec 024, D-301): lo lee {@code chat} ({@link ComunidadDeLaFormacionFinder}) y lo cambian
 * Administración y Alquimista. Fijar un tipo que ya está así no hace nada (idempotente).
 */
@Service
class ComunidadDeLaFormacionService implements ComunidadDeLaFormacionFinder, ConfigurarComunidadDeLaFormacionUseCase {

    private final RequireAdminGuard requireAdminGuard;
    private final TiposDeComunidadPort tipos;

    ComunidadDeLaFormacionService(RequireAdminGuard requireAdminGuard, TiposDeComunidadPort tipos) {
        this.requireAdminGuard = requireAdminGuard;
        this.tipos = tipos;
    }

    @Override
    public Set<TipoDeComunidad> prendidosDe(Formacion formacion) {
        return tipos.prendidosDe(formacion);
    }

    @Override
    @Transactional
    public Set<TipoDeComunidad> fijar(FijarTipoCommand command) {
        requireAdminGuard.requireAdminActivo(command.actor());
        Set<TipoDeComunidad> actuales = tipos.prendidosDe(command.formacion());
        Set<TipoDeComunidad> nuevos = actuales.isEmpty() ? EnumSet.noneOf(TipoDeComunidad.class) : EnumSet.copyOf(actuales);
        if (command.prendido()) {
            nuevos.add(command.tipo());
        } else {
            nuevos.remove(command.tipo());
        }
        if (!nuevos.equals(actuales)) {
            tipos.guardar(command.formacion(), nuevos, command.actor());
        }
        return nuevos;
    }
}
