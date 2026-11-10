package com.renaser.os.shared.application;

import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort.EnvioNuevo;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort.EnvioRegistrado;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Spec 048 (FR-008, FR-016, D13): la entrega de TODO lo hecho sin red en la fase 3, en un solo lugar para las ~25 escrituras de
 * Plan, Cierra tu día, Radar y planificar hábitos. Cada caso de uso le pasa lo que quiere aplicar y esta clase pone lo común,
 * <b>dentro de la transacción del propio caso de uso</b>:
 *
 * <ol>
 *   <li>un candado por {@code idCliente} para que dos pedidos iguales a la vez se turnen;</li>
 *   <li>si el envío ya llegó, responde lo mismo sin tocar nada: releyendo lo que quedó si se aplicó, o el mismo 409 si no;</li>
 *   <li>aplica ({@code aplicar}); si el caso de uso decide que no se aplica lanza {@link NoAplicadoSinRedException}, que aquí se
 *       anota como {@code NO_APLICADO} y se relanza (el caso de uso va con {@code noRollbackFor} para que la fila quede);</li>
 *   <li>escribe la fila de {@code envios_sin_red} y la línea {@code [offline]} del log, sin ningún texto de la persona.</li>
 * </ol>
 *
 * <p><b>Es una clase común y NO un bean.</b> Spring Modulith observa las entradas a cada módulo y su traza falla con un
 * {@code NullPointerException} al pintar un parámetro genérico como {@code Supplier<Aplicado<T>>} (el {@code T} no se resuelve,
 * {@code DefaultObservedModule.render}). Cada caso de uso la arma con el puerto y el reloj que ya recibe:
 * {@code new EntregaSinRed(envios, clock)}.
 */
public class EntregaSinRed {

    private static final Logger log = LoggerFactory.getLogger(EntregaSinRed.class);

    private final EnviosSinRedPort envios;
    private final Clock clock;

    public EntregaSinRed(EnviosSinRedPort envios, Clock clock) {
        this.envios = envios;
        this.clock = clock;
    }

    /** @param tipo {@code CIERRE_DEL_DIA}…; @param recurso la clave de lo tocado ({@code cierre:<fecha>}), sin textos de la persona */
    public record Envio(UserId actor, HechoSinRed sinRed, String tipo, String recurso) {
    }

    /**
     * Lo que sale de aplicar: la respuesta y, si quedó sin puntos, por qué (se repite igual en un reintento); {@code registroId} es el
     * id de lo creado cuando un reintento lo necesita para releerlo (opcional).
     */
    public record Aplicado<T>(T respuesta, String motivoSinPuntos, UUID registroId) {

        public static <T> Aplicado<T> de(T respuesta) {
            return new Aplicado<>(respuesta, null, null);
        }

        public static <T> Aplicado<T> sinPuntos(T respuesta, String motivoSinPuntos) {
            return new Aplicado<>(respuesta, motivoSinPuntos, null);
        }
    }

    /**
     * @param aplicar decide y aplica; lanza {@link NoAplicadoSinRedException} si no se debe aplicar
     * @param releer  la respuesta de un reintento de un envío ya aplicado: lo que quedó, con la misma forma que la primera vez
     */
    public <T> T entregar(Envio envio, Supplier<Aplicado<T>> aplicar, Function<EnvioRegistrado, T> releer) {
        exigirTransaccion();
        UUID id = envio.sinRed().idCliente();
        envios.tomarElCandado(id);
        var previo = envios.buscar(id, envio.actor());
        if (previo.isPresent()) {
            return repetir(envio, previo.get(), releer);
        }
        try {
            Aplicado<T> aplicado = aplicar.get();
            registrar(envio, aplicado.registroId(), aplicado.motivoSinPuntos(), null, null);
            return aplicado.respuesta();
        } catch (NoAplicadoSinRedException noAplicado) {
            registrar(envio, null, null, noAplicado.codigo(), noAplicado.getMessage());
            throw noAplicado;
        }
    }

    private <T> T repetir(Envio envio, EnvioRegistrado previo, Function<EnvioRegistrado, T> releer) {
        log.info("[offline] {} {} repetido (mismo envío): {}", envio.tipo(), envio.recurso(),
                previo.aplicado() ? "aplicado" : "no aplicado " + previo.noAplicado());
        if (!previo.aplicado()) {
            throw new NoAplicadoSinRedException(previo.noAplicado(), previo.mensaje());
        }
        return releer.apply(previo);
    }

    private void registrar(Envio envio, UUID registroId, String motivoSinPuntos, CodigoNoAplicado codigo, String mensaje) {
        HechoSinRed sinRed = envio.sinRed();
        envios.registrar(new EnvioNuevo(sinRed.idCliente(), envio.actor(), envio.tipo(), envio.recurso(), registroId, motivoSinPuntos,
                codigo, mensaje, sinRed.hechoEn(), sinRed.origenDeLaHora()));
        log.info("[offline] {} {} hecho {} (hora {}), llegó {}: {}", envio.tipo(), envio.recurso(), sinRed.hechoEn(),
                sinRed.origenDeLaHora(), clock.now(), codigo == null ? "aplicado" + (motivoSinPuntos == null ? "" : " sin puntos "
                        + motivoSinPuntos) : "no aplicado " + codigo);
    }

    private static void exigirTransaccion() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Lo hecho sin red se entrega dentro de la transacción del caso de uso");
        }
    }
}
