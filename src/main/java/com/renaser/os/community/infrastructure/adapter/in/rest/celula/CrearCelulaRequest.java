package com.renaser.os.community.infrastructure.adapter.in.rest.celula;

import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.UUID;

/**
 * {@code periodStart}/{@code periodEnd} (V48): las dos o ninguna — "septiembre, del 1 al 30". El
 * ultimo dia entra entero. Sin ellas, el grupo no caduca.
 *
 * <p>{@code type} y {@code capacity} entran con el SDD 003. {@code type} null = REGULAR, que es lo
 * que era todo grupo hasta ahora; RECEPTION es el grupo de bienvenida de siete dias, y hasta esta
 * revision no habia forma de crear uno desde la API — el administrador no podia armar la
 * bienvenida que el ingreso automatico necesita. {@code capacity} null = la capacidad de la
 * politica de la cohorte; en un RECEPTION se ignora porque no tiene tope (D-05).
 */
public record CrearCelulaRequest(@NotBlank String name, @NotNull UUID cohortId, String videoCallUrl,
                                  LocalDate periodStart, LocalDate periodEnd, String type,
                                  @Min(10) @Max(15) Integer capacity) {

    /** El vocabulario de la API va en ingles y el del dominio en castellano: la traduccion es
     * explicita y no un {@code valueOf} que dejaria pasar cualquier cosa con un 500. */
    public TipoCelula tipo() {
        if (type == null || type.isBlank()) {
            return null;
        }
        return switch (type.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "RECEPTION", "RECEPCION" -> TipoCelula.RECEPCION;
            case "REGULAR" -> TipoCelula.REGULAR;
            default -> throw new IllegalArgumentException("type debe ser REGULAR o RECEPTION, llego: " + type);
        };
    }
}
