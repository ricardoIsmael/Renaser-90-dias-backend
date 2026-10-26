package com.renaser.os.plataforma.application.ports.in;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

import java.util.List;

/**
 * Busca personas para sumarlas como probadoras (contrato fase 2 §3.4, {@code GET /soporte-ti/personas?q=}): por nombre
 * o correo, sin tildes ni mayúsculas, solo cuentas activas, hasta {@link #MAXIMO}.
 */
public interface BuscarPersonasUseCase {

    int MAXIMO = 20;

    /** Vacía si no se escribió nada. */
    List<PersonaEncontrada> buscar(AccesoDeConsola acceso, String texto);

    record PersonaEncontrada(UserId id, String nombre, String email, UserRole rol) {
    }
}
