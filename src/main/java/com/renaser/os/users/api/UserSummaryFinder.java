package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Puerto de entrada publico: la unica forma en que otro modulo consulta a `users`. */
public interface UserSummaryFinder {

    Optional<UserSummary> findById(UserId id);

    /**
     * Version EN LOTE: resuelve varios de una sola vez. Existe para que un listado
     * (ranking, feed del Muro) no dispare una consulta por fila — el N+1 clasico.
     * Los ids inexistentes simplemente no aparecen en el mapa.
     */
    Map<UserId, UserSummary> findByIds(Collection<UserId> ids);

    /**
     * Resolucion por correo, para que un administrador pueda designar a alguien sin conocer su
     * UUID (contracts.md: los guias se nombran por {@code userId} O {@code email}).
     *
     * <p>Vacio si no existe. NO crea la cuenta ni invita a nadie: designar una funcion es una
     * cosa y dar de alta un usuario es otra, y confundirlas convertiria un error de tipeo en una
     * cuenta fantasma.
     */
    /**
     * <b>Todo el padron de aprendices activos</b>, tenga o no actividad registrada (2026-09-15,
     * D-130). Lo pide el ranking: hasta hoy armaba sus candidatos desde
     * {@code puntajes_participante}, una tabla que se llena la primera vez que alguien SUMA
     * puntos — asi que un aprendiz recien incorporado no existia para la tabla, y una cohorte
     * entera que todavia no hizo nada daba un ranking vacio. Nadie tiene que "activarse" para
     * aparecer: se entra al ranking por estar en el programa.
     *
     * <p>Devuelve solo APRENDIZ y solo ACTIVO: un SUSPENDIDO no compite, y el staff tampoco.
     *
     * @return el padron completo, sin paginar — quien llama recorre una lista, no una pagina
     */
    List<UserSummary> aprendicesActivos();

    Optional<UserSummary> findByEmail(String email);
}
