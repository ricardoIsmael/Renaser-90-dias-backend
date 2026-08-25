package com.renaser.os.calendar.application.ports.out.participante;

import com.renaser.os.calendar.domain.model.evento.RolUsuario;
import com.renaser.os.shared.domain.UserId;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

/**
 * Copia PROPIA de `calendar` del patron documentado en
 * {@code rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort}
 * (CLAUDE.MD §5.1: cada modulo lee `participantes_programa`+`usuarios` con su propia query
 * nativa en vez de importar {@code users.api}).
 *
 * <p>El nombre es historico ("de participante") pero {@code deParticipante} resuelve por
 * {@code usuarios}, no por {@code participantes_programa}: {@code rol}/{@code suspendido}
 * existen para CUALQUIER usuario, tenga o no fila de programa (el programa de 90 dias es
 * obligatorio solo para APRENDIZ — baseline V1, tabla `participantes_programa`). Por eso
 * este puerto devuelve {@code Optional.empty()} solo cuando el {@code UserId} no existe en
 * {@code usuarios}; un ADMIN/ALCHEMIST/MENTOR sin perfil de programa SI aparece, con
 * {@code diaPrograma=0} y {@code celulaId=null} — el equivalente a como {@code
 * findViewerProgressPercent}/{@code findViewerCellId} (repository.ts, repo viejo) devuelven
 * 0/null para quien no tiene {@code traineeProfile}/{@code mentorProfile} en vez de fallar.
 *
 * <p>{@code celulaId} es la propia/liderada: para TRAINEE, {@code participantes_programa
 * .celula_id}; para MENTOR, la celula cuyo {@code celulas.mentor_id} es este usuario (a lo
 * sumo una — UNIQUE); {@code null} para el resto de roles y para quien aun no tiene celula.
 * Mismo criterio que {@code findViewerCellId} (repository.ts, repo viejo).
 *
 * <p>{@code zona} cae a {@code 'America/Lima'} (el mismo default de la columna
 * {@code participantes_programa.timezone} en el baseline) cuando no hay fila de programa.
 * Ningun caso de uso de este modulo la consume todavia — el fallback existe para que el
 * adaptador no tenga que inventar un {@code ZoneId} nulo, no porque haya una regla de
 * negocio detras.
 */
public interface ConsultarProgresoParticipanteCalendarPort {

    Optional<ProgresoParticipanteCalendar> deParticipante(UserId participanteId);

    record ProgresoParticipanteCalendar(int diaPrograma, ZoneId zona, RolUsuario rol, boolean suspendido,
                                         UUID celulaId) {
    }
}
