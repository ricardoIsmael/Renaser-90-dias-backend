package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.UUID;

/**
 * Los grupos que lidera un integrante del personal. <b>Lo implementa {@code community}</b> (dueño de los grupos) y lo consume {@code users}: la
 * dependencia va de {@code community} hacia {@code users}, como siempre, y {@code users} no importa a nadie. Lo usa la regla de FR-011 (spec 022):
 * no se le quita una formación a quien lidera allí un grupo.
 */
public interface GruposDelPersonalFinder {

    /** Los grupos vigentes que {@code persona} lidera como mentor (cualquiera sea su formación). */
    List<GrupoDelPersonal> queLidera(UserId persona);

    record GrupoDelPersonal(UUID id, String nombre, Formacion formacion) {
    }
}
