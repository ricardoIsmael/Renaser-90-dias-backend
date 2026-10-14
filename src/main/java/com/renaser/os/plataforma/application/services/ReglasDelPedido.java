package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.in.CambiarFuncionalidadUseCase.CambiarFuncionalidadCommand;
import com.renaser.os.plataforma.domain.model.estado.CambioDeFuncionalidad;
import com.renaser.os.users.api.UserRole;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Traduce el pedido de la consola al cambio del dominio: un rol que no existe, la versión o el estado que faltan son
 * un pedido inválido (400), nunca un valor adivinado.
 */
final class ReglasDelPedido {

    private ReglasDelPedido() {
    }

    static CambioDeFuncionalidad cambioDe(CambiarFuncionalidadCommand command) {
        if (command.prendidaGlobal() == null) {
            throw new IllegalArgumentException("Falta prendidaGlobal");
        }
        if (command.version() == null) {
            throw new IllegalArgumentException("Falta la version que estabas mirando");
        }
        return new CambioDeFuncionalidad(command.clave(), command.prendidaGlobal(), apagadaPara(command.porRol()),
                command.aviso(), command.motivo(), command.version());
    }

    private static Set<UserRole> apagadaPara(Map<String, Boolean> porRol) {
        Set<UserRole> apagadas = EnumSet.noneOf(UserRole.class);
        porRol.forEach((rol, prendida) -> {
            UserRole conocido = rolConocido(rol);
            if (Boolean.FALSE.equals(prendida)) {
                apagadas.add(conocido);
            }
        });
        return apagadas;
    }

    private static UserRole rolConocido(String rol) {
        try {
            return UserRole.valueOf(rol);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("El rol «" + rol + "» no existe.");
        }
    }
}
