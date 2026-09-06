package com.renaser.os.academy.infrastructure.adapter.in.rest.clasediaria;

import com.renaser.os.academy.application.ports.in.clasediaria.CompletarClaseDiariaUseCase.ClaseDiariaCompletada;

/**
 * Salida de {@code POST /api/v1/classroom/clase-diaria}.
 *
 * <p><b>Corregido 2026-09-05 (E-114).</b> El segundo campo se llamaba {@code habitTrackId},
 * espejando el shape del backend viejo (RenaserBack `clase-diaria/service.ts:64,85`:
 * {@code { leccionId, habitTrackId }}). Pero el contrato de ESTE backend
 * (`docs/api/CONTRATO_CONTENIDO_IA.md` §1.11-bis) documenta {@code registroHabitoId}, y contra ese
 * contrato se escribio el cliente movil (`completarClaseDiariaSchema` en `academySchemas.ts`). El
 * POST respondia 200 y cerraba el habito, pero el cliente rechazaba la respuesta al validarla y le
 * mostraba a la persona "No pudimos enviar tu resumen. Intenta de nuevo." sobre una operacion que
 * si habia ocurrido. Manda el contrato publicado, no el nombre del repo viejo: el resto del sistema
 * ya llama {@code registroHabitoId} a este identificador ({@link ClaseDiariaCompletada},
 * {@code EvidenciaResponse}, `CONTRATO_DIA_A_DIA.md`).
 */
public record CompletarClaseDiariaResponse(String leccionId, String registroHabitoId, int puntosOtorgados) {

    public static CompletarClaseDiariaResponse from(ClaseDiariaCompletada completada) {
        return new CompletarClaseDiariaResponse(completada.leccionId().value(),
                completada.registroHabitoId().toString(), completada.puntosOtorgados());
    }
}
