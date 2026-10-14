package com.renaser.os.plataforma.application.services;

import com.renaser.os.plataforma.application.ports.out.CargarEstadosPort;
import com.renaser.os.plataforma.domain.model.estado.FotoDeEstados;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

/**
 * La copia en memoria del estado de las funcionalidades de ESTA instancia (plan.md D3). Consultarla es leer una
 * referencia: no toca la base ni Redis en el camino de cada pedido (FR-011).
 *
 * <p>Se recarga al arrancar, cuando llega por Redis una versión más nueva que la suya y, de respaldo, cada 30 s. Si la
 * base no responde, conserva la última copia; si nunca la pudo leer, rige el valor por defecto del catálogo (spec,
 * «El servicio de interruptores falla»).
 */
@Service
public class CopiaDeEstados {

    private static final Logger log = LoggerFactory.getLogger(CopiaDeEstados.class);

    private final CargarEstadosPort cargarEstadosPort;
    private final AtomicReference<FotoDeEstados> foto = new AtomicReference<>(FotoDeEstados.vacia());

    public CopiaDeEstados(CargarEstadosPort cargarEstadosPort) {
        this.cargarEstadosPort = cargarEstadosPort;
    }

    public FotoDeEstados foto() {
        return foto.get();
    }

    /** Relee la base y reemplaza la copia entera. Un fallo no la vacía: se sigue con la anterior. */
    public synchronized void recargar() {
        try {
            FotoDeEstados nueva = FotoDeEstados.de(cargarEstadosPort.todos());
            FotoDeEstados anterior = foto.getAndSet(nueva);
            if (nueva.version() != anterior.version()) {
                log.info("[plataforma] estado de funcionalidades v{} -> v{}", anterior.version(), nueva.version());
            }
        } catch (RuntimeException e) {
            log.warn("[plataforma] no se pudo recargar el estado de funcionalidades; sigue la copia v{}",
                    foto.get().version(), e);
        }
    }

    /** El aviso de otra instancia (canal Redis): recarga solo si la versión es más nueva que la propia. */
    public void alAvisoDeVersion(long version) {
        if (version > foto.get().version()) {
            recargar();
        }
    }
}
