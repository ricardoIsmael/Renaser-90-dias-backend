package com.renaser.os.users.application.ports.in.formacion;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;

/**
 * Mantiene al día las formaciones del personal cuando la cuenta nace o cambia de rol (spec 022, FR-007): quien pasa a ser mentor, guía o líder sin
 * ninguna formación asignada queda en la de su programa o, si no tiene, en la Escuela; quien deja de ser personal deja de tener asignaciones.
 */
public interface MantenerFormacionesDelPersonalUseCase {

    void alDarseDeAlta(UserId usuario);

    void alCambiarDeRol(UserId usuario, UserRole rolNuevo);
}
