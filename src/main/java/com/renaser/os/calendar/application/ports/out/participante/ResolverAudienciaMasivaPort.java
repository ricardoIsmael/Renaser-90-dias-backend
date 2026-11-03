package com.renaser.os.calendar.application.ports.out.participante;

import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.shared.domain.DuracionDelPrograma;
import com.renaser.os.shared.domain.UserId;

import java.util.List;
import java.util.Set;

/**
 * Resolucion de audiencia EN LOTE para el generador de recordatorios — mismo motivo que el
 * repo viejo (repository.ts, seccion "Reminder audience (bulk)"): una consulta por evento,
 * nunca una por aprendiz. El padron de partida son los aprendices activos (y, solo en
 * ROLES, los roles que el evento nombra) — igual que {@code resolveAudience} en
 * reminderService.ts, NO "todo el que canViewEvent dejaria pasar" (eso incluiria a cada
 * ADMIN en cada evento).
 */
public interface ResolverAudienciaMasivaPort {

    /** ALL_MEMBERS del repo viejo: todo aprendiz activo. */
    List<UserId> traineesActivos();

    /** ROLES del repo viejo: usuarios activos con cualquiera de estos roles (no acotado a TRAINEE). */
    List<UserId> activosConRoles(Set<RolUsuario> roles);

    /** NIVEL_MINIMO/CURSO del repo viejo: aprendices activos con su dia de programa
     * ({@code null} si aun no tiene ficha de participante). */
    List<ParticipanteConDia> traineesActivosConDiaPrograma();

    /** {@code duracionDias}: la duración de su programa (90 o 365, spec 022), para medir su nivel sobre ella. */
    record ParticipanteConDia(UserId id, Integer diaPrograma, int duracionDias) {

        public ParticipanteConDia(UserId id, Integer diaPrograma) {
            this(id, diaPrograma, DuracionDelPrograma.POR_DEFECTO);
        }
    }
}
