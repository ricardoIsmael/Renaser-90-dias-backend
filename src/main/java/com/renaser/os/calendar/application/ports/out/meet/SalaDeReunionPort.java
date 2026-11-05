package com.renaser.os.calendar.application.ports.out.meet;

import com.renaser.os.calendar.domain.model.meet.AccesoSalaMeet;
import com.renaser.os.calendar.domain.model.meet.ArtefactosDeLaSala;

import java.util.Optional;

/**
 * La sala de reunión que Renaser le pide a Google (spec 040). El puerto dice QUÉ necesita Renaser —crear, actualizar y borrar la sala de una fecha y
 * dejarla abierta o con permiso—, no cómo se consigue: el adaptador de Google usa la Calendar API y la Meet REST API con la cuenta organizadora.
 *
 * <p>Todas las operaciones son idempotentes por {@link PedidoDeSala#idGoogle()}: pedir dos veces lo mismo no deja dos salas (FR-007).
 *
 * <p>Fallan con una de estas excepciones, nunca con la de HTTP: {@link CuentaGoogleNoConectadaException} (no hay cuenta o está revocada),
 * {@link CuentaGoogleRevocadaException} (Google acaba de rechazar el permiso), {@link GoogleNoRespondeException} (red, 5xx, 429: se puede reintentar)
 * y {@link GoogleRechazoException} (Google dijo que no: reintentar no sirve).
 */
public interface SalaDeReunionPort {

    /** Crea la sala; si Google ya tenía el evento (409), toma la suya. {@link ResultadoDeSala#lista()} es falso si Google sigue creándola. */
    ResultadoDeSala crear(PedidoDeSala pedido);

    /** Cambia título, hora o duración del evento de Google. El enlace no cambia. */
    void actualizar(PedidoDeSala pedido);

    /** Borra el evento de Google sin avisar a nadie. Que ya no exista cuenta como hecho. */
    void borrar(String idGoogle);

    /**
     * Deja la sala abierta o con permiso y la lee de vuelta, porque un 200 no prueba que quedó (FR-023).
     *
     * @return lo que Google confirmó; vacío si lo leído no es ninguno de los dos valores
     */
    Optional<AccesoSalaMeet> fijarAcceso(String enlace, AccesoSalaMeet acceso);

    /**
     * Pide que la sala grabe, transcriba y tome notas sola (FR-036) y la lee de vuelta. Si Google no lo deja —la cuenta conectada no tiene el plan—
     * no es un error: se devuelve lo que la sala tiene, con {@code false} en lo que se pidió y no quedó (FR-037).
     *
     * @param pedidos lo que está en {@code true} se pide; lo que está en {@code null} no se toca
     * @return lo que Google confirmó de lo pedido ({@code true} o {@code false}); {@code null} en lo que no se pidió
     */
    ArtefactosDeLaSala fijarArtefactos(String enlace, ArtefactosDeLaSala pedidos);
}
