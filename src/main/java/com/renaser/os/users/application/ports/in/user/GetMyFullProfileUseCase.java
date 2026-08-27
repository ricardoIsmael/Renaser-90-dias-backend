package com.renaser.os.users.application.ports.in.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.user.User;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Version enriquecida de {@link GetMyProfileUseCase} para {@code POST /api/v1/users/me}:
 * ademas del {@link User}, trae el resumen de su participacion en el programa de 90 dias
 * (4to agregado de `users`, {@code ParticipacionPrograma}) cuando existe fila para el actor
 * - hueco #1 de docs/PLAN_INTEGRACION_FRONTEND.md ("TraineeProfile" que el frontend espera
 * dentro de la respuesta de perfil).
 *
 * <p>Deliberadamente NO se reusa {@link GetMyProfileUseCase}: ese puerto ya lo consume el
 * flujo de login (AutenticacionController/IniciarSesionUseCase) y cambiar su forma de
 * retorno arrastraria esos otros llamadores sin necesidad. Es la composicion que exige
 * CLAUDE.MD §5.4.6 ("si hacen falta dos casos de uso, falta uno que los componga") resuelta
 * como un caso de uso nuevo en vez de que el controller orqueste dos.
 */
public interface GetMyFullProfileUseCase {

    MyProfile getMyFullProfile(UserId userId);

    /** {@code traineeProfile} es {@code null} si el actor no tiene fila en `participantes_programa`
     * (nunca se inscribio, o desactivo su "seguimiento personal" de staff). */
    record MyProfile(User user, TraineeProfileSummary traineeProfile) {
    }

    /**
     * Proyeccion de {@code ParticipacionPrograma} (dominio interno de `users`) para el
     * `traineeProfile` de la respuesta HTTP - no se expone el agregado completo (§8).
     *
     * @param goalType             vocabulario ingles (ver {@code TipoMeta}), null si no eligio meta todavia
     * @param isProgramCompleted   ver docs/FEATURE_POST_PROGRAM.md - una vez true, currentPhase
     *                             se queda en la fase final para siempre; es el UNICO campo que
     *                             sirve para detectar la graduacion
     */
    record TraineeProfileSummary(String personalChallengeName, LocalDate startDate, String goalType,
                                  boolean isProgramCompleted, Instant programCompletedAt, int postProgramDay) {
    }
}
