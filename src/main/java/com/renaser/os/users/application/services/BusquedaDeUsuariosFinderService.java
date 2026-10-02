package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BusquedaDeUsuariosFinder;
import com.renaser.os.users.application.ports.out.user.BuscarUsuariosPorTextoPort;
import com.renaser.os.users.domain.model.user.TextoDeBusqueda;
import org.springframework.stereotype.Service;

import java.util.Set;

/** Lo que otros módulos preguntan para buscar personas por nombre o correo (D-249). */
@Service
class BusquedaDeUsuariosFinderService implements BusquedaDeUsuariosFinder {

    private final BuscarUsuariosPorTextoPort buscarPorTexto;

    BusquedaDeUsuariosFinderService(BuscarUsuariosPorTextoPort buscarPorTexto) {
        this.buscarPorTexto = buscarPorTexto;
    }

    @Override
    public Set<UserId> coincidenCon(String texto) {
        return TextoDeBusqueda.de(texto).map(buscarPorTexto::coincidenCon).orElse(Set.of());
    }
}
