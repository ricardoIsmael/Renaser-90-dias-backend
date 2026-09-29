package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.participante.GruposEnCursoPort;
import com.renaser.os.chat.application.ports.out.participante.PertenenciaVigentePort;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Quién puede ver el chat de un grupo (CELULA). Es la rama de grupo de la regla de
 * {@code AutorizacionDeConversacionService}, y la usan las cuatro copias de esa regla
 * ({@code MensajeService}, {@code ConversacionService}, {@code PresenciaService} y aquella): antes
 * cada una le preguntaba directo a {@link PertenenciaVigentePort}, y con D-225 la rama dejó de ser
 * una sola línea.
 *
 * <p><b>Dos maneras de ver un grupo:</b>
 * <ol>
 *   <li>Pertenecer hoy al grupo (mentor, aprendiz, guía, soporte asignado). Sin cambios.</li>
 *   <li>(D-225) Tener un rol que ve todos los grupos, con la cuenta activa, mientras el grupo siga en
 *       curso. Hoy ese rol es solo ADMIN. El Admin NO se vuelve integrante: no figura en la lista de
 *       integrantes, no recibe el push de cada mensaje (los avisos salen de la pertenencia vigente) y
 *       no tiene fila en {@code participantes_conversacion}, así que su contador de no leídos de esos
 *       grupos es 0 y marcar leído no deja rastro (no mueve los ✓✓ de D-208).</li>
 * </ol>
 */
@Component
public class AccesoAChatsDeGrupo {

    /**
     * Los roles que ven el chat de TODOS los grupos en curso sin estar asignados (D-225). Es el único
     * lugar donde se decide: el ALQUIMISTA queda afuera hasta que el dueño lo confirme, y sumarlo es
     * agregar {@code UserRole.ALCHEMIST} acá.
     */
    private static final Set<UserRole> ROLES_QUE_VEN_TODOS_LOS_GRUPOS = EnumSet.of(UserRole.ADMIN);

    private final PertenenciaVigentePort pertenenciaVigentePort;
    private final GruposEnCursoPort gruposEnCursoPort;
    private final UserSummaryFinder userSummaryFinder;

    public AccesoAChatsDeGrupo(PertenenciaVigentePort pertenenciaVigentePort, GruposEnCursoPort gruposEnCursoPort,
                               UserSummaryFinder userSummaryFinder) {
        this.pertenenciaVigentePort = pertenenciaVigentePort;
        this.gruposEnCursoPort = gruposEnCursoPort;
        this.userSummaryFinder = userSummaryFinder;
    }

    /** Pertenece hoy al grupo, o lo ve por su rol mientras el grupo está en curso. */
    public boolean puedeVer(UUID celulaId, UserId usuarioId) {
        if (pertenenciaVigentePort.perteneceAlGrupo(celulaId, usuarioId)) {
            return true;
        }
        return veTodosLosGrupos(usuarioId) && gruposEnCursoPort.estaEnCurso(celulaId);
    }

    /**
     * Los grupos que el usuario ve por su rol, además de los suyos: todos los que están en curso para
     * un Admin activo; ninguno para cualquier otro.
     */
    public List<UUID> gruposQueVePorSuRol(UserId usuarioId) {
        return veTodosLosGrupos(usuarioId) ? gruposEnCursoPort.gruposEnCurso() : List.of();
    }

    /** El rol y el estado de AHORA. Un usuario que no existe no ve nada: falla cerrado. */
    private boolean veTodosLosGrupos(UserId usuarioId) {
        return userSummaryFinder.findById(usuarioId)
                .filter(usuario -> usuario.status() == UserStatus.ACTIVE)
                .map(usuario -> ROLES_QUE_VEN_TODOS_LOS_GRUPOS.contains(usuario.role()))
                .orElse(false);
    }
}
