package com.renaser.os.notifications.domain.model.whatsapp;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Un recordatorio de evento por WhatsApp a una persona, en el estado en que va (spec 041, FR-020 a FR-022). <b>Sin el texto del mensaje, sin
 * el numero y sin respuestas</b>: solo lo que hace falta para no mandarlo dos veces, anotar como termino y saber a quien pertenece cuando
 * llega un «No recibir más».
 *
 * <p>{@code clave} es la deduplicacion (la misma del aviso: una por fila de la cola de recordatorios, que distingue cada uno de los cuatro
 * avisos y cada persona). {@code id} es el {@code externalId} que viaja al proveedor y la carga del boton de baja.
 */
public record EntregaDeWhatsApp(UUID id, UUID clave, UUID eventoId, UserId usuarioId, int avisoMin, EstadoDeEntrega estado,
                                String motivo, String wamid, String proveedorId, Instant creadoEn, Instant actualizadoEn) {

    public EntregaDeWhatsApp {
        Objects.requireNonNull(id, "id es obligatorio");
        Objects.requireNonNull(clave, "clave es obligatoria");
        Objects.requireNonNull(eventoId, "eventoId es obligatorio");
        Objects.requireNonNull(usuarioId, "usuarioId es obligatorio");
        Objects.requireNonNull(estado, "estado es obligatorio");
        Objects.requireNonNull(creadoEn, "creadoEn es obligatorio");
        Objects.requireNonNull(actualizadoEn, "actualizadoEn es obligatorio");
        if (avisoMin <= 0) {
            throw new IllegalArgumentException("avisoMin tiene que ser positivo");
        }
    }

    /** La fila que se aparta ANTES de llamar al proveedor. */
    public static EntregaDeWhatsApp apartar(UUID id, UUID clave, UUID eventoId, UserId usuarioId, int avisoMin, Instant ahora) {
        return new EntregaDeWhatsApp(id, clave, eventoId, usuarioId, avisoMin, EstadoDeEntrega.ENVIANDO, null, null, null, ahora, ahora);
    }

    /** Lo que no se mando, con su motivo: sin numero, llego tarde, el evento ya empezo, tope mensual. */
    public static EntregaDeWhatsApp omitida(UUID id, UUID clave, UUID eventoId, UserId usuarioId, int avisoMin, String motivo,
                                            Instant ahora) {
        return new EntregaDeWhatsApp(id, clave, eventoId, usuarioId, avisoMin, EstadoDeEntrega.OMITIDO, motivo, null, null, ahora, ahora);
    }

    /** Como termino la llamada al proveedor. */
    public EntregaDeWhatsApp conResultado(ResultadoDelEnvio resultado, Instant ahora) {
        return new EntregaDeWhatsApp(id, clave, eventoId, usuarioId, avisoMin, resultado.estado(), resultado.motivo(),
                resultado.wamid(), resultado.proveedorId(), creadoEn, ahora);
    }
}
