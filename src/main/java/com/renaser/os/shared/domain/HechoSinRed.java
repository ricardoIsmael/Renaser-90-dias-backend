package com.renaser.os.shared.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Spec 048 (FR-007, FR-013): lo que acompaña a una escritura hecha SIN RED cuando llega al servidor: la hora real en que la
 * persona la hizo, el identificador del envío (un reintento con el mismo vuelve el mismo resultado, FR-008) y cómo se calculó
 * la hora. En la fase 3 viaja en tres cabeceras ({@code X-Hecho-En}, {@code X-Id-Cliente}, {@code X-Origen-De-La-Hora});
 * sin ellas no hay {@code HechoSinRed} (nulo) y el pedido es uno normal.
 *
 * @param origenDeLaHora solo se anota en el rastro; nulo se lee como {@link OrigenDeLaHora#TELEFONO_CORREGIDO}
 */
public record HechoSinRed(Instant hechoEn, UUID idCliente, OrigenDeLaHora origenDeLaHora) {

    public HechoSinRed {
        Objects.requireNonNull(hechoEn, "hechoEn");
        Objects.requireNonNull(idCliente, "idCliente");
        origenDeLaHora = origenDeLaHora == null ? OrigenDeLaHora.TELEFONO_CORREGIDO : origenDeLaHora;
    }
}
