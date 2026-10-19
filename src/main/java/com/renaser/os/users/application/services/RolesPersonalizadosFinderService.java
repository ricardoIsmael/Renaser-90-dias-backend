package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.RolPersonalizadoResumen;
import com.renaser.os.users.api.RolesPersonalizadosFinder;
import com.renaser.os.users.application.ports.out.rolpersonalizado.CargarRolesPersonalizadosPort;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** La lectura de roles personalizados para otros módulos (ver {@link RolesPersonalizadosFinder}; D-272). */
@Service
class RolesPersonalizadosFinderService implements RolesPersonalizadosFinder {

    private final CargarRolesPersonalizadosPort cargar;

    RolesPersonalizadosFinderService(CargarRolesPersonalizadosPort cargar) {
        this.cargar = cargar;
    }

    @Override
    public Optional<RolPersonalizadoResumen> delUsuario(UserId usuario) {
        return cargar.vigenteDe(usuario).flatMap(this::porId);
    }

    @Override
    public List<RolPersonalizadoResumen> todos() {
        Map<UUID, Long> personas = cargar.personasPorRol();
        return cargar.todos().stream()
                .map(rol -> ResumenDeRolPersonalizado.de(rol, personas.getOrDefault(rol.id(), 0L)))
                .toList();
    }

    @Override
    public Optional<RolPersonalizadoResumen> porId(UUID id) {
        return cargar.porId(id).map(rol -> ResumenDeRolPersonalizado.de(rol, cargar.personasCon(id)));
    }

    @Override
    public Map<UserId, UUID> asignaciones() {
        return cargar.asignacionesVigentes();
    }
}
