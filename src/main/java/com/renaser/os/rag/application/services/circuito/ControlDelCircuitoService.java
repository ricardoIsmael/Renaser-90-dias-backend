package com.renaser.os.rag.application.services.circuito;

import com.renaser.os.rag.application.ports.in.circuito.CircuitoDeLaIa;
import com.renaser.os.rag.application.ports.out.circuito.AvisarCambioDelCircuitoPort;
import com.renaser.os.rag.application.ports.out.circuito.EstadoCompartidoDelCircuitoPort;
import com.renaser.os.rag.domain.model.circuito.CambioDelCircuito;
import com.renaser.os.rag.domain.model.circuito.CircuitoDeIa;
import com.renaser.os.rag.domain.model.circuito.EstadoDelCircuito;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import com.renaser.os.rag.domain.model.circuito.ReglasDelCircuito;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.FuncionalidadApagadaException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;

/**
 * El circuito de la IA (spec 002, FR-016; plan.md D7) sobre el estado compartido en Redis. Las reglas son de
 * {@link CircuitoDeIa}; acá está el orden de las operaciones contra Redis y lo que pasa alrededor de cada cambio.
 *
 * <p><b>Camino de siempre sin Redis.</b> Con el circuito cerrado en la copia en memoria, pedir paso y anotar un éxito
 * no tocan Redis: es el 99,9 % de los pedidos. Solo una falla, o un circuito que no está cerrado, van a Redis.
 *
 * <p><b>Varias instancias.</b> Cada cambio es un compare-and-set por versión: si dos instancias ven la quinta falla a
 * la vez, una abre y la otra relee y ya lo encuentra abierto; si dos pedidos quieren ser la prueba, uno solo la toma.
 * Solo quien gana el cambio audita, publica el evento y avisa a las demás.
 *
 * <p><b>Falla abierto.</b> Si Redis no responde, se deja pasar y se anota en el log: un circuito roto no puede apagar
 * SER.
 */
@Service
public class ControlDelCircuitoService implements CircuitoDeLaIa {

    private static final Logger log = LoggerFactory.getLogger(ControlDelCircuitoService.class);
    /** Reintentos del compare-and-set ante otra instancia que escribió primero. */
    private static final int INTENTOS = 3;

    private final EstadoCompartidoDelCircuitoPort estadoCompartido;
    private final AvisarCambioDelCircuitoPort avisarCambio;
    private final CopiaDelCircuito copia;
    private final AnotadorDeCambiosDelCircuito anotador;
    private final ReglasDelCircuito reglas;
    private final Clock clock;

    public ControlDelCircuitoService(EstadoCompartidoDelCircuitoPort estadoCompartido,
                                     AvisarCambioDelCircuitoPort avisarCambio, CopiaDelCircuito copia,
                                     AnotadorDeCambiosDelCircuito anotador, ReglasDelCircuito reglas, Clock clock) {
        this.estadoCompartido = estadoCompartido;
        this.avisarCambio = avisarCambio;
        this.copia = copia;
        this.anotador = anotador;
        this.reglas = reglas;
        this.clock = clock;
    }

    @Override
    public void exigirPaso(ParteDeLaIa parte) {
        if (!dejaPasar(parte)) {
            throw new FuncionalidadApagadaException(parte.clave(), EstadoAutomaticoDeLaIa.AVISO);
        }
    }

    @Override
    public boolean dejaPasar(ParteDeLaIa parte) {
        if (copia.de(parte).estado() == EstadoDelCircuito.CERRADO) {
            return true;
        }
        try {
            return pasoSegunLoCompartido(parte);
        } catch (RuntimeException e) {
            log.warn("[rag.circuito] no se pudo consultar el circuito de {}; se deja pasar ({})", parte.clave(),
                    e.getClass().getSimpleName());
            return true;
        }
    }

    @Override
    public void anotarFalla(ParteDeLaIa parte) {
        try {
            Instant ahora = clock.now();
            int fallas = estadoCompartido.anotarFalla(parte, ahora, reglas.ventana());
            aplicar(parte, actual -> actual.trasFalla(fallas, ahora, reglas));
        } catch (RuntimeException e) {
            log.warn("[rag.circuito] no se pudo anotar la falla del proveedor en {} ({})", parte.clave(),
                    e.getClass().getSimpleName());
        }
    }

    @Override
    public void anotarExito(ParteDeLaIa parte) {
        if (copia.de(parte).estado() == EstadoDelCircuito.CERRADO) {
            return;
        }
        try {
            aplicar(parte, actual -> actual.trasExito(clock.now()));
        } catch (RuntimeException e) {
            log.warn("[rag.circuito] no se pudo anotar la respuesta del proveedor en {} ({})", parte.clave(),
                    e.getClass().getSimpleName());
        }
    }

    @Override
    public boolean cortado(ParteDeLaIa parte) {
        return copia.de(parte).cortaElPaso(clock.now());
    }

    /** Cerrado pasa; abierto con la espera corriendo no; vencida, pasa solo quien toma la prueba. */
    private boolean pasoSegunLoCompartido(ParteDeLaIa parte) {
        Instant ahora = clock.now();
        CircuitoDeIa actual = estadoCompartido.leer(parte);
        copia.guardar(actual);
        if (actual.estado() == EstadoDelCircuito.CERRADO) {
            return true;
        }
        if (!actual.admitePrueba(ahora)) {
            return false;
        }
        CircuitoDeIa conPrueba = actual.conPruebaEnCurso(ahora, reglas);
        if (!estadoCompartido.reemplazar(actual, conPrueba)) {
            return false;
        }
        copia.guardar(conPrueba);
        avisarCambio.avisar(parte);
        log.info("[rag.circuito] {}: este pedido es la prueba (vence {})", parte.clave(), conPrueba.pruebaVence());
        return true;
    }

    private void aplicar(ParteDeLaIa parte, Function<CircuitoDeIa, Optional<CambioDelCircuito>> transicion) {
        for (int intento = 0; intento < INTENTOS; intento++) {
            CircuitoDeIa actual = estadoCompartido.leer(parte);
            Optional<CambioDelCircuito> cambio = transicion.apply(actual);
            if (cambio.isEmpty()) {
                copia.guardar(actual);
                return;
            }
            if (estadoCompartido.reemplazar(actual, cambio.get().despues())) {
                alCambiar(cambio.get());
                return;
            }
        }
        log.warn("[rag.circuito] otra instancia cambió el circuito de {} {} veces seguidas; se deja como está",
                parte.clave(), INTENTOS);
    }

    private void alCambiar(CambioDelCircuito cambio) {
        copia.guardar(cambio.despues());
        if (cambio.cierra()) {
            estadoCompartido.olvidarFallas(cambio.parte());
        }
        avisarCambio.avisar(cambio.parte());
        log.warn("[rag.circuito] {} {}: {}", cambio.parte().clave(), cambio.despues().estado(), cambio.causa());
        try {
            anotador.anotar(cambio);
        } catch (RuntimeException e) {
            log.error("[rag.circuito] el circuito de {} cambió pero no quedó en la auditoría ni se avisó",
                    cambio.parte().clave(), e);
        }
    }
}
