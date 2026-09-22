package com.renaser.os.points.application.ports.in.puntaje;

import com.renaser.os.points.domain.model.ajuste.AjustePuntos;
import com.renaser.os.shared.application.SelfValidating;
import com.renaser.os.shared.domain.UserId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public interface AjustarPuntosManualmenteUseCase {

    AjustePuntos ajustarManualmente(AjustarPuntosManualmenteCommand command);

    /**
     * Ajuste manual de puntos hecho por un ADMIN/ALCHEMIST activo. {@code delta} exige
     * {@code > 0}: el dueño del producto decidió que el sistema no resta puntos a nadie, por
     * ningún motivo (D-145; la única resta automática que existía, {@code
     * SantuarioService.romper()}, ya se eliminó). Este comando era el único hueco que quedaba
     * abierto — los otros cinco llamadores de {@code AjustarPuntosPort.ajustar} (evidence,
     * habits, rocks) ya se autolimitan a sumar antes de invocarlo.
     *
     * <p>La guarda vive ACÁ, en el comando self-validating, y no en:
     * <ul>
     *   <li>{@code PuntajeController}: el controller es tonto — solo deserializa, valida con
     *       {@code @Valid}, invoca un caso de uso y mapea la salida. Meter un {@code if} de
     *       negocio ahí viola esa regla directamente.</li>
     *   <li>{@code PuntajeParticipante.registrarAjuste}: ese método de dominio es compartido por
     *       los otros cinco llamadores. Moverle la guarda cambiaría el contrato de un método con
     *       seis invocadores para cerrar un agujero que tiene un solo origen real (este comando),
     *       y encima llega tarde: hoy {@code registrarAjuste} acota el SALDO con
     *       {@code Math.max(saldo + delta, 0)} — eso acota el RESULTADO, no impide la resta. Un
     *       delta negativo que no cruce el piso se aplicaría entero y en silencio.</li>
     * </ul>
     *
     * <p>Por eso la guarda va en la puerta de entrada propia del ajuste manual, antes de que el
     * delta llegue a ningún otro lado. Rechaza con
     * {@link jakarta.validation.ConstraintViolationException} (400 vía
     * {@code GlobalExceptionHandler}) en vez de aceptar el pedido y aplicarlo recortado o en 0:
     * quien administra tiene que enterarse de que el ajuste que pidió no se hizo, no ver un 201
     * que no cambió nada.
     */
    record AjustarPuntosManualmenteCommand(@NotNull UserId participanteId,
                                            @Positive(message = "El ajuste manual de puntos debe ser mayor que "
                                                    + "0: no se permite restar ni registrar un ajuste en cero")
                                            int delta, String nota, @NotNull UserId actorId) {

        public AjustarPuntosManualmenteCommand {
            SelfValidating.validateConstructorArgs(AjustarPuntosManualmenteCommand.class, participanteId, delta,
                    nota, actorId);
        }
    }
}
