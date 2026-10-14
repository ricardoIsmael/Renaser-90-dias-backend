package com.renaser.os.rag.application.ports.out.circuito;

import com.renaser.os.rag.domain.model.circuito.CircuitoDeIa;
import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;

import java.time.Duration;
import java.time.Instant;

/**
 * El estado del circuito de la IA que comparten todas las instancias (spec 002, «CircuitoDeIa (memoria compartida)»;
 * plan.md D7). Hoy es Redis. Cualquier método puede lanzar si el almacén no responde: quien llama decide qué hacer
 * (el circuito falla abierto: sin Redis, SER sigue).
 */
public interface EstadoCompartidoDelCircuitoPort {

    /** Lo guardado; sin nada guardado, {@link CircuitoDeIa#cerrado}. */
    CircuitoDeIa leer(ParteDeLaIa parte);

    /**
     * Guarda {@code nuevo} solo si lo guardado sigue en {@code esperado.version()} (compare-and-set). Dos instancias
     * que ven la quinta falla a la vez no abren dos veces: una gana, la otra relee.
     *
     * @return {@code false} si otra instancia escribió antes
     */
    boolean reemplazar(CircuitoDeIa esperado, CircuitoDeIa nuevo);

    /** Anota una falla en {@code ahora} y devuelve cuántas hay en la ventana que termina ahí, contando esta. */
    int anotarFalla(ParteDeLaIa parte, Instant ahora, Duration ventana);

    /** Cuántas fallas hay en la ventana que termina en {@code ahora} (para la consola). */
    int fallasEnVentana(ParteDeLaIa parte, Instant ahora, Duration ventana);

    /** Al cerrarse el circuito: las fallas de antes ya no cuentan para la próxima apertura. */
    void olvidarFallas(ParteDeLaIa parte);
}
