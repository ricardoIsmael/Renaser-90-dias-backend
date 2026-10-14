package com.renaser.os.rag.application.services.circuito;

import com.renaser.os.rag.application.ports.out.circuito.EstadoCompartidoDelCircuitoPort;
import com.renaser.os.rag.domain.model.circuito.CircuitoDeIa;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/**
 * La copia en memoria del circuito de cada parte, en esta instancia (spec 002, FR-011: consultar un interruptor no
 * puede costar un viaje a Redis). Se pone al día al cambiar algo acá, con el aviso de las demás instancias y con una
 * recarga periódica; si Redis no responde, sigue con lo último que vio.
 *
 * <p>Nunca retrocede: una lectura vieja que llega tarde (versión menor) no pisa a una más nueva. La única excepción es
 * Redis vacío (se reinició sin persistencia, o se borraron las claves): entonces vuelve la versión 0 y manda eso.
 */
@Component
public class CopiaDelCircuito {

    private static final Logger log = LoggerFactory.getLogger(CopiaDelCircuito.class);

    private final EstadoCompartidoDelCircuitoPort estadoCompartido;
    private final Map<ParteDeLaIa, CircuitoDeIa> circuitos = new EnumMap<>(ParteDeLaIa.class);
    /** Lo que se suma a la versión cada vez que Redis volvió a 0, para que la que ve la app no retroceda. */
    private long desfase;

    public CopiaDelCircuito(EstadoCompartidoDelCircuitoPort estadoCompartido) {
        this.estadoCompartido = estadoCompartido;
        for (ParteDeLaIa parte : ParteDeLaIa.values()) {
            circuitos.put(parte, CircuitoDeIa.cerrado(parte));
        }
    }

    public synchronized CircuitoDeIa de(ParteDeLaIa parte) {
        return circuitos.get(parte);
    }

    /** Lo que se acaba de leer o escribir en Redis. */
    public synchronized void guardar(CircuitoDeIa circuito) {
        CircuitoDeIa actual = circuitos.get(circuito.parte());
        if (circuito.version() >= actual.version()) {
            circuitos.put(circuito.parte(), circuito);
        } else if (circuito.version() == 0) {
            // Redis se vació: manda lo que hay (cerrado), y la versión sigue subiendo en vez de volver atrás.
            desfase += actual.version() + 1;
            circuitos.put(circuito.parte(), circuito);
        }
    }

    /** Relee todas las partes; una que falla queda con lo que tenía. */
    public void recargar() {
        for (ParteDeLaIa parte : ParteDeLaIa.values()) {
            try {
                guardar(estadoCompartido.leer(parte));
            } catch (RuntimeException e) {
                log.warn("[rag.circuito] no se pudo releer el circuito de {}; sigue la copia anterior ({})",
                        parte.clave(), e.getClass().getSimpleName());
            }
        }
    }

    /**
     * La suma de las versiones de todas las partes: cambia con cada apertura, cierre o toma de la prueba, y es la misma
     * en todas las instancias que leyeron lo mismo. No baja mientras la instancia corre: si Redis se vació, suma un
     * desfase y sigue subiendo (desde ahí cada instancia puede dar un número distinto; la huella del ETag igual cambia
     * con el contenido).
     */
    public synchronized long version() {
        return desfase + circuitos.values().stream().mapToLong(CircuitoDeIa::version).sum();
    }
}
