package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.UserStatus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Panel admin de aprendices (gap #7 de docs/PLAN_INTEGRACION_FRONTEND.md): listado paginado. */
public interface ListTraineesUseCase {

    PaginaTrainees listar(ListTraineesCommand command);

    record ListTraineesCommand(UserId actorId, int page, int size) {

        public ListTraineesCommand {
            Objects.requireNonNull(actorId, "actorId es obligatorio");
            if (page < 0) {
                throw new IllegalArgumentException("page no puede ser negativo");
            }
            if (size <= 0 || size > 200) {
                throw new IllegalArgumentException("size debe estar entre 1 y 200");
            }
        }
    }

    record PaginaTrainees(List<ResumenTraineeAdmin> contenido, long total, int page, int size) {
    }

    /**
     * Vive aca (ports.in) y no en el puerto de salida que lo produce
     * ({@code ConsultarResumenParticipacionPort}) para que el controller pueda mapearlo sin
     * importar `ports.out` — ArchitectureTest.controllersDoNotTouchPersistence lo exige
     * (CLAUDE.MD sec. 5.4.6). El puerto de salida importa este tipo, no al reves: esa
     * direccion (out -> in) no tiene ninguna regla que la prohiba.
     */
    record ResumenTraineeAdmin(UserId id, String fullName, String email, UserStatus status, int diaPrograma,
                                FasePrograma fase, UUID celulaId, UserId mentorId) {
    }
}
