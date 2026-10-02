package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.Set;

/**
 * Qué cuentas están cerradas esperando su borrado definitivo (D-243).
 *
 * <p>Cuando una persona elimina su cuenta, la cuenta se cierra al instante y durante 30 días
 * todavía se puede recuperar (un Admin la recupera si la persona escribe a soporte). En ese plazo
 * sus filas siguen en la base —si no, recuperarla no tendría sentido— pero los demás ya no la tienen
 * que ver: sale de rankings, grupos, comunidad y listados. Cada módulo filtra sus listados con este
 * finder en vez de leer {@code usuarios}.
 *
 * <p>Es distinto de {@link UserStatus#SUSPENDED}: una cuenta cerrada también queda SUSPENDED (para que
 * nadie entre, las sesiones caigan y los avisos dejen de llegar), pero una suspensión a secas sigue
 * mostrándose como hasta ahora. Este finder responde solo por las cerradas para eliminar.
 */
public interface CuentasCerradasFinder {

    /** De los ids dados, los que están cerrados para eliminar. Vacío si ninguno. Una consulta. */
    Set<UserId> cerradasEntre(Collection<UserId> ids);
}
