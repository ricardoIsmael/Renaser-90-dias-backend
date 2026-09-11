package com.renaser.os.rocks.application.ports.out.participante;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

/**
 * Copia PROPIA de `rocks` del patrón documentado en
 * `docs/MODULO_PHASECONTRACTS.md` §2: `users.api.UserSummary` filtra
 * `UserRole`/`UserStatus`, tipos internos de `users` fuera de su
 * `@NamedInterface("api")`. En vez de importar eso, `rocks` lee
 * `participantes_programa`+`usuarios` con su propia query nativa y su propio
 * enum local `RolParticipante`.
 *
 * <p>Más rica que la de `phasecontracts` (que solo necesitaba día de programa
 * + rol + suspendido): las ventanas de planificación (§ver
 * `VentanaPlanificacionSemanal`/`VentanaPlanificacionDiaria`) necesitan la
 * ZONA HORARIA real del participante, y el cálculo de número de semana
 * necesita `fechaInicio` — ambas columnas ya existen en
 * `participantes_programa` (`timezone`, `fecha_inicio`).
 */
public interface ConsultarProgresoParticipanteRocksPort {

    Optional<ProgresoParticipanteRocks> deParticipante(UserId participanteId);


    /**
     * `participantes_programa.programa_activado_en IS NOT NULL` — el reloj de los 90 dias
     * arrancó. NO significa "esta inscrito": un TRAINEE recien aprobado tiene fila y este campo
     * en `null` hasta que completa primer login + Ficha + Terminos.
     *
     * <p>Existe para E-169: el staff que activa su seguimiento personal
     * (`POST /api/v1/mentor/activate-tracking`) queda con este campo puesto, y es lo unico que
     * distingue a un mentor que SI cursa el programa de uno que no.
     */
    record ProgresoParticipanteRocks(int diaPrograma, LocalDate fechaInicio, ZoneId zona, RolParticipante rol,
                                      boolean suspendido, boolean programaActivado) {
    }

    /** Espejo LOCAL (a este modulo) del enum Postgres `rol_usuario`. */
    enum RolParticipante {
        ALCHEMIST,
        ADMIN,
        MENTOR_LEAD,
        MENTOR,
        TRAINEE
    }
}
