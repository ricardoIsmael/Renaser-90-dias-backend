package com.renaser.os.rag.application.ports.out.cuota;

import com.renaser.os.shared.domain.UserId;

import java.time.Duration;
import java.time.LocalDate;

/**
 * Los segundos de voz en vivo que ya uso una persona en un dia (D-162). Vive en Redis, como la
 * cuota de mensajes ({@link ControlCuotaRenasiaPort}).
 *
 * <p>El dia lo decide quien llama, con la zona de la persona (regla 02): este puerto no sabe de
 * zonas horarias, solo guarda un contador por fecha.
 */
public interface ControlCuotaVozEnVivoPort {

    Duration usadoEn(UserId actorId, LocalDate dia);

    /** Suma {@code tramo} (en segundos enteros) al dia y devuelve el total. */
    Duration sumar(UserId actorId, LocalDate dia, Duration tramo);
}
