package com.renaser.os.calendar.domain.model.evento;

/**
 * Espejo LOCAL (a este modulo) del enum Postgres {@code rol_usuario}, en ingles — mismo
 * criterio documentado en {@code rocks.application.ports.out.participante
 * .ConsultarProgresoParticipanteRocksPort.RolParticipante}: `calendar` no importa
 * {@code users.api.UserRole} para no acoplarse a tipos de otro modulo mas alla de lo
 * imprescindible, y define su propia copia con el mismo vocabulario en ingles que ya usa
 * el resto del backend (D-21).
 *
 * <p>Se usa tanto para el rol del ACTOR/VISOR (resuelto por
 * {@code ConsultarProgresoParticipanteCalendarPort}) como para {@code roles_destino_evento}
 * (audiencia {@code ROLES}) — un solo vocabulario dentro del modulo.
 */
public enum RolUsuario {

    ALCHEMIST,
    ADMIN,
    MENTOR_LEAD,
    MENTOR,
    TRAINEE
}
