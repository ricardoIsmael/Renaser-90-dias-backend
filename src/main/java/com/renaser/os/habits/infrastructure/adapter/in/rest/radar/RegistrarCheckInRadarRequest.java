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
 *
 * <p>{@code dictado} (spec 030, D-297): opcional; ausente o {@code null} es {@code false}. La app lo manda en {@code true}
 * cuando al menos una respuesta se dicto con el reconocimiento de voz del telefono. El audio nunca viaja.
 */
public record RegistrarCheckInRadarRequest(
        @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String whatAmIDoing,
        @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String whatAmIThinking,
        @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String whatAmIFeeling,
        @NotNull @Min(RegistroRadar.NIVEL_ENERGIA_MIN) @Max(RegistroRadar.NIVEL_ENERGIA_MAX) Integer energyLevel,
        @NotBlank @Size(max = RegistroRadar.TEXTO_MAX_LENGTH) String whatAmIAvoiding,
        Boolean dictado) {

    /** {@code dictado} sin el campo vale {@code false}: un APK anterior no lo conoce. */
    public boolean fueDictado() {
        return Boolean.TRUE.equals(dictado);
    }
}
