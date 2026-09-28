package com.renaser.os.onboarding.application.ports.in.caja;

import com.renaser.os.onboarding.domain.model.caja.DatosDelEnvio;
import com.renaser.os.onboarding.domain.model.caja.DestinoAlternativo;
import com.renaser.os.onboarding.domain.model.caja.EstadoCaja;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.List;

/** «Tu Caja Renaser»: el aprendiz ve en qué va, cambia a dónde va y confirma que llegó (D-219). */
public interface MiCajaUseCase {

    MiCaja ver(UserId actorId);

    MiCaja cambiarDestino(UserId actorId, PedidoDeDestino destino);

    MiCaja confirmarRecibida(UserId actorId);

    /**
     * @param pasos      los cinco pasos que ve (en evaluación → en revisión → armando → en camino → entregada),
     *                   con cuándo llegó a cada uno ({@code null} si todavía no, o si no se sabe)
     * @param envioDatos {@code null} hasta que salga
     */
    record MiCaja(EstadoCaja estado, List<PasoVisible> pasos, DatosDelEnvio envioDatos, boolean puedeConfirmar,
                  boolean puedeCambiarDestino, DestinoAlternativo destino) {
    }

    record PasoVisible(EstadoCaja estado, Instant en) {
    }

    /** Lo que escribió el aprendiz; vacío = sin respuesta. */
    record PedidoDeDestino(String otraDireccion, String otroCelular, String quienRecibe, String referencias,
                           String provincia) {
    }
}
