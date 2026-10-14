package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.out.CargarDesplieguesPort;
import com.renaser.os.plataforma.domain.model.despliegue.FotoDeDespliegues;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * La copia en memoria de los despliegues en curso de ESTA instancia (contrato fase 2 §3.2), con el mismo patrón que
 * {@link CopiaDeEstados}: la regla de disponibilidad y las métricas la leen en cada pedido sin ir a la base.
 *
 * <p>Se recarga al arrancar, después de cada cambio de esta instancia, con cada aviso del canal Redis
 * {@code renaser:funcionalidades} y, de respaldo, cada 30 s. A diferencia de los interruptores, el aviso no se filtra
 * por versión: un despliegue terminado borra su fila, así que «la versión mayor» puede bajar. Recargar son dos lecturas
 * chicas y los cambios los hace una persona a mano: recargar de más no cuesta nada.
 *
 * <p>Si la base no responde se conserva la copia anterior; si nunca se pudo leer, no hay nada en despliegue (todo llega
 * a todos, como antes de la fase 2).
 */
@Service
public class CopiaDeDespliegues {

    private static final Logger log = LoggerFactory.getLogger(CopiaDeDespliegues.class);

    private final CargarDesplieguesPort cargarDesplieguesPort;
    private final AtomicReference<FotoDeDespliegues> foto = new AtomicReference<>(FotoDeDespliegues.vacia());

    public CopiaDeDespliegues(CargarDesplieguesPort cargarDesplieguesPort) {
        this.cargarDesplieguesPort = cargarDesplieguesPort;
    }

    public FotoDeDespliegues foto() {
        return foto.get();
    }

    /** Relee la base y reemplaza la copia entera. Un fallo no la vacía: se sigue con la anterior. */
    public synchronized void recargar() {
        try {
            FotoDeDespliegues nueva = FotoDeDespliegues.de(cargarDesplieguesPort.todos());
            FotoDeDespliegues anterior = foto.getAndSet(nueva);
            if (nueva.cantidad() != anterior.cantidad()) {
                log.info("[plataforma] funcionalidades en despliegue: {} -> {}", anterior.cantidad(), nueva.cantidad());
            }
        } catch (RuntimeException e) {
            log.warn("[plataforma] no se pudieron recargar los despliegues; sigue la copia anterior ({} en curso)",
                    foto.get().cantidad(), e);
        }
    }
}
