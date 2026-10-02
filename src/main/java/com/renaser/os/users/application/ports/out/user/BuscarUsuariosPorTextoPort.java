package com.renaser.os.users.application.ports.out.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.TextoDeBusqueda;

import java.util.Set;

/** Las cuentas cuyo nombre o correo, normalizados, contienen el texto (D-249). */
public interface BuscarUsuariosPorTextoPort {

    Set<UserId> coincidenCon(TextoDeBusqueda texto);
}
