package com.renaser.os.habits.infrastructure.adapter.in.rest.radar;

import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Nombres de campo literales del contrato viejo (`CreateRadarEntryInput`,
 * `src/features/daily-checkin/schema.ts` del repo Backend90dias) — no los
 * `queHago`/`quePienso`/... del dominio en español, ni el `snake_case` de la
 * tabla Supabase `radar_entries` que usa hoy el cliente (D-36).
 */
public record RegistrarCheckInRadarRequest(
        @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String whatAmIDoing,
        @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String whatAmIThinking,
        @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String whatAmIFeeling,
        @NotNull @Min(RegistroRadar.NIVEL_ENERGIA_MIN) @Max(RegistroRadar.NIVEL_ENERGIA_MAX) Integer energyLevel,
        @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String whatAmIAvoiding) {
}
