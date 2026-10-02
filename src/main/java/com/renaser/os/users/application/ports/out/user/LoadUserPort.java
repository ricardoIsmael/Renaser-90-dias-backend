package com.renaser.os.users.application.ports.out.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Lo que la aplicacion necesita para LEER usuarios. No sabe de JPA ni de SQL. */
public interface LoadUserPort {

    Optional<User> byId(UserId id);

    Optional<User> byEmail(Email email);

    /** Lectura EN LOTE, para no disparar una consulta por id (N+1). */
    List<User> byIds(Collection<UserId> ids);

    /**
     * Una tanda de hasta {@code limite} cuentas cerradas cuya gracia vencio ({@code baja_solicitada_en
     * <= corte}), en orden de id y despues de {@code despuesDe} ({@code null} = desde el principio).
     * Solo ids: el unico uso que se les da es borrarlas (D-243, barrido paginado de la regla 02).
     */
    List<UserId> cerradasVencidas(Instant corte, UserId despuesDe, int limite);

    /** De estos ids, los de cuentas cerradas para eliminar (D-243). Una consulta. */
    Set<UserId> cerradasEntre(Collection<UserId> ids);

    /**
     * Panel admin de staff (gap #6): pagina de usuarios cuyo rol esta en {@code roles},
     * filtrando ademas por {@code statusFilter} si no es {@code null}. {@code page}/{@code size}
     * son primitivos (no {@code Pageable}: el puerto no conoce Spring Data, solo el
     * adaptador lo traduce) — mismo criterio que {@code LoadRecordatorioPort} en `calendar`.
     */
    List<User> byRoles(Collection<UserRole> roles, UserStatus statusFilter, int page, int size);

    long countByRoles(Collection<UserRole> roles, UserStatus statusFilter);
}
