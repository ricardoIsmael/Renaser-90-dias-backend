package com.renaser.os.academy.application.ports.in.clasediaria;

import com.renaser.os.academy.domain.model.curso.LeccionId;
import com.renaser.os.habits.api.CompletarClaseDiariaHabitoUseCase;
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

    /**
     * Longitud minima del resumen. Era 20, espejo de {@code CLASE_DIARIA_SUMMARY_MIN_LENGTH}
     * (RenaserBack {@code clase-diaria/schema.ts:3}); el dueño del producto lo fijó en 15 al
     * especificar el flujo de Training (2026-09-04, textual: "mínimo 15 letras hasta 2000").
     * Se referencia la constante de {@code habits} en vez de repetir el número para que las dos
     * mitades del flujo no puedan divergir.
     */
    int RESUMEN_MIN_LENGTH = CompletarClaseDiariaHabitoUseCase.RESUMEN_MIN_LENGTH;

    /** Longitud maxima del resumen (dueño del producto, 2026-09-04: "hasta 2000"). */
    int RESUMEN_MAX_LENGTH = CompletarClaseDiariaHabitoUseCase.RESUMEN_MAX_LENGTH;

    ClaseDiariaCompletada completar(CompletarClaseDiariaCommand command);

    record CompletarClaseDiariaCommand(@NotNull UserId actorId, @NotNull LeccionId leccionId,
                                        @NotBlank
                                        @Size(min = RESUMEN_MIN_LENGTH, max = RESUMEN_MAX_LENGTH)
                                        String resumen) {

        public CompletarClaseDiariaCommand {
            SelfValidating.validateConstructorArgs(CompletarClaseDiariaCommand.class, actorId, leccionId, resumen);
        }
    }

    record ClaseDiariaCompletada(LeccionId leccionId, UUID registroHabitoId, int puntosOtorgados) {
    }
}
