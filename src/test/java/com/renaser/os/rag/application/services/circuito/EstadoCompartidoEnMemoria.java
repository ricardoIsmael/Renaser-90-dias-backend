package com.renaser.os.rag.application.services.circuito;

import com.renaser.os.rag.application.ports.out.circuito.EstadoCompartidoDelCircuitoPort;
import com.renaser.os.rag.domain.model.circuito.CircuitoDeIa;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * El Redis del circuito, en memoria y con las mismas reglas (compare-and-set por versión, ventana que poda lo viejo):
 * lo comparten varias instancias del servicio en la misma prueba. {@link #caido} simula a Redis sin responder.
 */
final class EstadoCompartidoEnMemoria implements EstadoCompartidoDelCircuitoPort {

    private final Map<ParteDeLaIa, CircuitoDeIa> circuitos = new EnumMap<>(ParteDeLaIa.class);
    private final Map<ParteDeLaIa, List<Instant>> fallas = new EnumMap<>(ParteDeLaIa.class);
    int lecturas;
    boolean caido;

    @Override
    public synchronized CircuitoDeIa leer(ParteDeLaIa parte) {
        disponible();
        lecturas++;
        return circuitos.getOrDefault(parte, CircuitoDeIa.cerrado(parte));
    }

    @Override
    public synchronized boolean reemplazar(CircuitoDeIa esperado, CircuitoDeIa nuevo) {
        disponible();
        long actual = circuitos.getOrDefault(nuevo.parte(), CircuitoDeIa.cerrado(nuevo.parte())).version();
        if (actual != esperado.version()) {
            return false;
        }
        circuitos.put(nuevo.parte(), nuevo);
        return true;
    }

    @Override
    public synchronized int anotarFalla(ParteDeLaIa parte, Instant ahora, Duration ventana) {
        disponible();
        fallas.computeIfAbsent(parte, p -> new ArrayList<>()).add(ahora);
        fallas.get(parte).removeIf(falla -> !falla.isAfter(ahora.minus(ventana)));
        return fallas.get(parte).size();
    }

    @Override
    public synchronized int fallasEnVentana(ParteDeLaIa parte, Instant ahora, Duration ventana) {
        disponible();
        return (int) fallas.getOrDefault(parte, List.of()).stream().filter(f -> f.isAfter(ahora.minus(ventana)))
                .count();
    }

    @Override
    public synchronized void olvidarFallas(ParteDeLaIa parte) {
        disponible();
        fallas.remove(parte);
    }

    /** Redis que se reinició sin persistencia. */
    synchronized void vaciar() {
        circuitos.clear();
        fallas.clear();
    }

    private void disponible() {
        if (caido) {
            throw new IllegalStateException("Redis no responde (simulado)");
        }
    }
}
