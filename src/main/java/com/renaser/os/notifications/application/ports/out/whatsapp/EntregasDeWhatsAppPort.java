package com.renaser.os.notifications.application.ports.out.whatsapp;

import com.renaser.os.notifications.domain.model.whatsapp.EntregaDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Donde se anota cada recordatorio por WhatsApp (tabla {@code entregas_whatsapp}, spec 041, FR-030). Sin el texto del mensaje, sin el numero y sin
 * llaves foraneas a otros modulos.
 */
public interface EntregasDeWhatsAppPort {

    /**
     * Aparta la entrega ANTES de llamar al proveedor (FR-020): {@code INSERT ... ON CONFLICT (clave) DO NOTHING} en su propia transaccion, que queda
     * confirmada aunque el listener falle despues. Devuelve {@code false} si la clave ya existia (una reentrega, dos pasadas, dos instancias): entonces no se
     * llama al proveedor.
     */
    boolean apartar(EntregaDeWhatsApp entrega);

    /** Anota como termino la llamada (estado, motivo, wamid y el id del proveedor), en su propia transaccion. */
    void guardarResultado(EntregaDeWhatsApp entrega);

    /**
     * Pasa la entrega a {@code nuevo} SOLO si su estado de ahora puede avanzar a el ({@link EstadoDeEntrega#desdeLosQuePuedeAvanzarA()}): un aviso tardio no
     * deshace nada. Devuelve si se aplico.
     */
    boolean avanzarEstado(UUID id, EstadoDeEntrega nuevo, String motivo, String wamid, String proveedorId, Instant ahora);

    Optional<EntregaDeWhatsApp> porId(UUID id);

    Optional<EntregaDeWhatsApp> porProveedorId(String proveedorId);

    /** La entrega a la que responde una persona: el wamid del mensaje que ella respondio. */
    Optional<EntregaDeWhatsApp> porWamid(String wamid);

    /** Cuantos mensajes salieron en el intervalo {@code [desde, hasta)}, sin contar los omitidos ni los que fallaron (el tope mensual). */
    long contarQueSalieron(Instant desde, Instant hasta);

    /** Los conteos de un evento para el resumen de Administracion (FR-029). */
    ConteoDeEntregas conteoDelEvento(UUID eventoId);

    /**
     * @param porEstado         cuantas entregas hay en cada estado (todas, incluidas las omitidas)
     * @param omitidasSinNumero cuantas de las omitidas lo fueron por «Sin WhatsApp»
     * @param personasSinNumero a cuantas personas distintas les toco y no tenian un WhatsApp en la ficha
     */
    record ConteoDeEntregas(Map<EstadoDeEntrega, Integer> porEstado, int omitidasSinNumero, int personasSinNumero) {
        public ConteoDeEntregas {
            porEstado = Map.copyOf(porEstado);
        }
    }

    /** Retencion de 12 meses. */
    int purgarAnterioresA(Instant limite);
}
