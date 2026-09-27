package com.renaser.os.users.infrastructure.adapter.in.rest.admin;

import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.participante.GetTraineeDetailUseCase.TraineeDetail;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Detalle del panel admin de aprendices (gap #7): usuario + resumen de programa.
 *
 * <p>{@code startDate} es el Dia 1 ELEGIDO, {@code null} mientras la persona no activo su programa
 * (D-201, E-336). Antes salia la fecha provisional del alta, y el panel —que para {@code null} ya
 * tenia el texto "Todavia no eligio su Dia 1"— ofrecia "Cambiar dia del programa" a alguien sin
 * Dia 1, y el servidor respondia 409.
 */
public record TraineeDetailResponse(String id, String email, String fullName, UserRole role, UserStatus status,
                                     String avatarUrl, boolean inscrito, int programDay, LocalDate startDate,
                                     FasePrograma phase, UUID cellId, String mentorId,
                                     UltimoAjusteDiaResponse lastDayAdjustment) {

    public static TraineeDetailResponse from(TraineeDetail detail) {
        var user = detail.user();
        var participacion = detail.participacion();
        return new TraineeDetailResponse(user.id().toString(), user.email().value(), user.fullName(), user.role(),
                user.status(), user.avatarUrl(), participacion.inscrito(), participacion.diaPrograma(),
                participacion.diaUnoElegido(), participacion.fase(), participacion.celulaId(),
                participacion.mentorId() == null ? null : participacion.mentorId().toString(),
                UltimoAjusteDiaResponse.from(detail.ultimoAjuste()));
    }
}
