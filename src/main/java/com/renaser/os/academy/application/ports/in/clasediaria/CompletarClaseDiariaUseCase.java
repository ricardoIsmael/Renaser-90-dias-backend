package com.renaser.os.academy.application.ports.in.clasediaria;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * POST /api/v1/classroom/clase-diaria — completa la Clase Diaria de HOY del aprendiz.
 * Espejo de {@code completeClaseDiaria} (RenaserBack {@code clase-diaria/service.ts:60-90}).
 *
 * <p>Relacion real con `habits` (investigada contra el repo viejo, ver
 * `docs/MODULO_ACADEMY.md` §6 AC-13 y `docs/PLAN_INTEGRACION_FRONTEND.md` #23): completar la
 * Clase Diaria son DOS escrituras relacionadas pero separadas, no un solo concepto —
 * {@code clase-diaria/service.ts:77-83}:
 * <pre>
 * // Se guarda antes de completar el hábito. Ambos pasos son idempotentes...
 * const completed = await habitService.completeTodayDailyClassWithSummary(userId, resumen)
 * if (!completed.success) return completed
 * await repo.markLeccionCompleted(userId, clase.leccionId)
 * </pre>
 * (1) cierra el registro de HOY del habito de catalogo {@code DAILY_CLASS} — puntos,
 * racha y ventana de entrega, dominio exclusivo de {@code habits}, delegado vía
 * {@code habits.api.CompletarClaseDiariaHabitoUseCase} (nunca se importan sus internos); y
 * (2) marca la lección correspondiente como vista en {@code leccion_progreso}, dominio propio
 * de {@code academy} ya cubierto por {@link CompletarLeccionUseCase}. Este caso de uso solo
 * orquesta ambos, en el mismo orden que el repo viejo (primero el hábito, después la lección)
 * y revalida en servidor que la lección pedida es la Clase Diaria real de hoy — el cliente
 * nunca decide qué lección se completa.
 */
public interface CompletarClaseDiariaUseCase {

    /** Longitud minima del resumen — espejo de {@code CLASE_DIARIA_SUMMARY_MIN_LENGTH}
     * (RenaserBack {@code clase-diaria/schema.ts:3}). */
    int RESUMEN_MIN_LENGTH = 20;

    ClaseDiariaCompletada completar(CompletarClaseDiariaCommand command);

    record CompletarClaseDiariaCommand(@NotNull UserId actorId, @NotNull LeccionId leccionId,
                                        @NotBlank @Size(min = RESUMEN_MIN_LENGTH) String resumen) {

        public CompletarClaseDiariaCommand {
            SelfValidating.validateConstructorArgs(CompletarClaseDiariaCommand.class, actorId, leccionId, resumen);
        }
    }

    record ClaseDiariaCompletada(LeccionId leccionId, UUID registroHabitoId, int puntosOtorgados) {
    }
}
