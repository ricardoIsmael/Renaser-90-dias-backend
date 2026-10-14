package com.renaser.os.rag.application.ports.in.circuito;

import com.renaser.os.rag.domain.model.circuito.ParteDeLaIa;
import com.renaser.os.shared.domain.ProveedorIaNoDisponibleException;
import reactor.core.publisher.Flux;

import java.util.function.Supplier;

/**
 * El circuito automático de la IA visto por quien llama al proveedor (spec 002, FR-016; plan.md D7): pedir paso antes
 * de llamarlo y contar cómo le fue después. Lo manual (los interruptores de Soporte TI) no pasa por acá: lo combina
 * {@code InterruptoresDeSer}.
 *
 * <p><b>Qué cuenta como falla:</b> solo lo que es del proveedor y transitorio —
 * {@link ProveedorIaNoDisponibleException} (429, 408, 5xx, timeouts) y que la voz en vivo no abra—. Un error nuestro
 * (un 400, un dato raro) no abre el circuito. <b>Qué cuenta como éxito:</b> una respuesta completa del modelo de esa
 * parte (el stream del chat, el audio de la voz, la sesión en vivo abierta). Los embeddings solo cuentan fallas: si el
 * modelo de embeddings responde pero el del chat está saturado, la prueba la tiene que resolver el chat.
 *
 * <p>Nunca lanza por un problema del almacén compartido: sin Redis el circuito deja pasar (falla abierto), porque un
 * circuito roto no puede apagar SER.
 */
public interface CircuitoDeLaIa {

    /**
     * Lanza {@code FuncionalidadApagadaException} con el aviso automático si el circuito no deja llamar al proveedor
     * ahora. Con el circuito medio abierto, el pedido que pasa es la prueba.
     */
    void exigirPaso(ParteDeLaIa parte);

    /** Igual que {@link #exigirPaso}, sin lanzar. */
    boolean dejaPasar(ParteDeLaIa parte);

    void anotarFalla(ParteDeLaIa parte);

    void anotarExito(ParteDeLaIa parte);

    /** Lectura en memoria, sin tocar Redis: para un proceso de fondo que solo quiere saber si vale la pena llamar. */
    boolean cortado(ParteDeLaIa parte);

    /** El stream del modelo: completo es éxito, {@link ProveedorIaNoDisponibleException} es falla, lo demás nada. */
    default <T> Flux<T> vigilando(ParteDeLaIa parte, Flux<T> respuesta) {
        return respuesta.doOnComplete(() -> anotarExito(parte)).doOnError(error -> {
            if (error instanceof ProveedorIaNoDisponibleException) {
                anotarFalla(parte);
            }
        });
    }

    /** Una llamada que solo puede sumar fallas (los embeddings): la excepción sigue su camino. */
    default <T> T contandoFallas(ParteDeLaIa parte, Supplier<T> llamada) {
        try {
            return llamada.get();
        } catch (ProveedorIaNoDisponibleException falla) {
            anotarFalla(parte);
            throw falla;
        }
    }
}
