package com.renaser.os.users.application.ports.in.participante;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Panel admin de personas (gap #7 de docs/PLAN_INTEGRACION_FRONTEND.md): listado paginado.
 *
 * <p><b>Corregido 2026-09-16 (D-138).</b> Aca decia "panel admin de aprendices", y era literal: el
 * listado filtraba {@code u.rol = 'APRENDIZ'} y las 7 cuentas de staff no aparecian nunca. Ahora
 * trae TODOS los roles, cada fila con el suyo. El nombre de los metodos sigue diciendo
 * "trainees"/"aprendices" porque es el contrato HTTP ya publicado
 * ({@code GET /api/v1/admin/trainees}) y renombrarlo seria romper al cliente por prolijidad.
 */
public interface ListTraineesUseCase {

    PaginaTrainees listar(ListTraineesCommand command);

    /**
     * @param busqueda     nombre o correo, resuelto EN LA BASE. {@code null} = sin recorte.
     * @param soloSinGrupo la cola operativa de la pantalla de resumen: a quien hay que ubicar.
     *                     <b>Sigue siendo solo de APRENDICES</b> aunque el listado ya traiga todos
     *                     los roles (D-138): a un miembro de staff no se le puede asignar celula,
     *                     asi que contarlo aca lo dejaria en una cola de la que nada lo saca.
     */
    record ListTraineesCommand(UserId actorId, int page, int size, String busqueda, boolean soloSinGrupo) {

        /** Sobrecarga previa al SDD 003: listado completo, sin filtros. */
        public ListTraineesCommand(UserId actorId, int page, int size) {
            this(actorId, page, size, null, false);
        }

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
     *
     * @param rol el rol de la persona, <b>dato y no deduccion</b> (D-138). Mientras la lista fue
     *            solo de aprendices, el rol se inferia de "en que listado aparecio"; con los cinco
     *            roles mezclados esa inferencia ya no existe. Se agrego <b>al final</b> para no
     *            mover los parametros que ya usaba el adaptador.
     */
    record ResumenTraineeAdmin(UserId id, String fullName, String email, UserStatus status, int diaPrograma,
                                FasePrograma fase, UUID celulaId, UserId mentorId, UserRole rol) {
    }
}
