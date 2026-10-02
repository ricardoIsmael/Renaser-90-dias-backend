package com.renaser.os.users.application.ports.out.eliminacion;

import com.renaser.os.users.domain.model.user.RegistroDeEliminacion;

/** Auditoria de cierres, recuperaciones y borrados de cuentas (V90, D-243). Solo inserta. */
public interface RegistrarEliminacionPort {

    void registrar(RegistroDeEliminacion registro);
}
