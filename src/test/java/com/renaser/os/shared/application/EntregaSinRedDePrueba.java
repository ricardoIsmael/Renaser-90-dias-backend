package com.renaser.os.shared.application;

import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.UserId;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * La entrega de lo hecho sin red para las pruebas de los casos de uso (spec 048, fase 3): la real con un puerto en memoria, que
 * recuerda lo que se registra, y la transacción simulada como activa. Cada prueba la arma en su {@code @BeforeEach} con
 * {@link #nueva(Clock)} y llama {@link #terminar()} al acabar.
 */
public final class EntregaSinRedDePrueba {

    private final Map<UUID, EnviosSinRedPort.EnvioNuevo> registrados = new HashMap<>();
    private final EntregaSinRed entrega;
    private final EnviosSinRedPort puerto;

    private EntregaSinRedDePrueba(Clock clock) {
        this.puerto = new EnviosEnMemoria();
        this.entrega = new EntregaSinRed(puerto, clock);
        TransactionSynchronizationManager.setActualTransactionActive(true);
    }

    public static EntregaSinRedDePrueba nueva(Clock clock) {
        return new EntregaSinRedDePrueba(clock);
    }

    public EntregaSinRed entrega() {
        return entrega;
    }

    /** El puerto en memoria, para armar los casos de uso que reciben el puerto y arman su propia entrega. */
    public EnviosSinRedPort puerto() {
        return puerto;
    }

    /** Lo que quedó registrado para ese envío, aplicado o no. */
    public Optional<EnviosSinRedPort.EnvioNuevo> registro(UUID idCliente) {
        return Optional.ofNullable(registrados.get(idCliente));
    }

    public Optional<CodigoNoAplicado> noAplicado(UUID idCliente) {
        return registro(idCliente).map(EnviosSinRedPort.EnvioNuevo::noAplicado);
    }

    public void terminar() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    private final class EnviosEnMemoria implements EnviosSinRedPort {

        @Override
        public void tomarElCandado(UUID idCliente) {
            // Una sola hebra en las pruebas de unidad: no hay con quién turnarse.
        }

        @Override
        public Optional<EnvioRegistrado> buscar(UUID idCliente, UserId participanteId) {
            return Optional.ofNullable(registrados.get(idCliente)).filter(e -> e.participanteId().equals(participanteId))
                    .map(e -> new EnvioRegistrado(e.registroId(), e.motivoSinPuntos(), e.noAplicado(), e.mensaje()));
        }

        @Override
        public void registrar(EnvioNuevo envio) {
            registrados.putIfAbsent(envio.idCliente(), envio);
        }
    }
}
