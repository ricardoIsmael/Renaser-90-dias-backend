package com.renaser.os.habits.api;

import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * Cierra el hábito de catálogo {@code DAILY_CLASS} para HOY, con el resumen de la clase
 * como respuesta de texto — la mitad de "completar la Clase Diaria" que le corresponde a
 * {@code habits} (la otra mitad, marcar la lección vista, la resuelve {@code academy} con
 * su propio {@code ProgresoLeccion}; ver {@code docs/MODULO_ACADEMY.md} §6, AC-13).
 *
 * <p>Espejo de {@code completeTodayDailyClassWithSummary}/{@code completeDailyClassTrack}
 * (RenaserBack {@code habits/service.ts:1747-1811}): localiza el track de HOY del hábito
 * cuya {@code claveSistema} es {@code DAILY_CLASS} sin exponer su identidad al llamador —
 * {@code academy} nunca ve ni maneja un {@code RegistroHabitoId}.
 *
 * <p><b>Por qué es un puerto propio y no un genérico "completar cualquier hábito por
 * clave":</b> en el repo viejo, el bypass de evidencia de {@code completeDailyClassTrack}
 * está deliberadamente cerrado a {@code DAILY_CLASS} — la ruta pública de completar hábitos
 * (H-02) no puede activarlo para NINGÚN otro hábito. Generalizarlo abriría, para cualquier
 * módulo futuro que importe {@code habits.api}, un atajo para completar hábitos con
 * evidencia obligatoria sin subirla. Este puerto solo sabe hacer una cosa.
 */
public interface CompletarClaseDiariaHabitoUseCase {

    /** Identidad funcional estable del hábito de catálogo (tabla {@code habitos}, columna {@code clave_sistema}). */
    String CLAVE_SISTEMA_DAILY_CLASS = "DAILY_CLASS";

    /**
     * Longitud mínima del resumen. Repetida acá como red de seguridad propia de este módulo, sin
     * confiar en que el llamador ya validó el contrato HTTP.
     *
     * <p><b>15, no 20 (dueño del producto, 2026-09-04):</b> el valor original espejaba
     * {@code DAILY_CLASS_SUMMARY_MIN_LENGTH} (RenaserBack {@code habits/service.ts:1543}), que era
     * 20. Al especificar el flujo de la Clase Diaria en Training el dueño pidió, textual,
     * "mínimo 15 letras hasta 2000". Se toma su número: el 20 era un espejo del backend viejo, no
     * una decisión de este producto. Debe seguir siendo el MISMO valor que
     * {@code CompletarClaseDiariaUseCase.RESUMEN_MIN_LENGTH} (academy) — si divergen, el aprendiz
     * pasa una validación y choca con la otra.
     */
    int RESUMEN_MIN_LENGTH = 15;

    /** Longitud máxima del resumen (dueño del producto, 2026-09-04: "hasta 2000"). Antes no había
     * tope acá; el único límite efectivo era el {@code @Size(max = 4000)} de
     * {@code CompletarRegistroRequest}, que es el del campo de texto genérico, no el de este flujo. */
    int RESUMEN_MAX_LENGTH = 2000;

    /**
     * Idempotente: si el registro de hoy ya está COMPLETADO, devuelve el resultado ya
     * otorgado sin volver a completarlo ni a sumar puntos — ninguna transición del dominio
     * abandona un estado terminal (ver {@code EstadoRegistro}).
     */
    RegistroCompletado completarDeHoy(CompletarClaseDiariaHabitoCommand command);

    record CompletarClaseDiariaHabitoCommand(@NotNull UserId participanteId,
                                              @NotBlank
                                              @Size(min = RESUMEN_MIN_LENGTH, max = RESUMEN_MAX_LENGTH)
                                              String resumen) {

        public CompletarClaseDiariaHabitoCommand {
            SelfValidating.validateConstructorArgs(CompletarClaseDiariaHabitoCommand.class, participanteId, resumen);
        }
    }

    record RegistroCompletado(UUID registroId, int puntosOtorgados) {
    }
}
