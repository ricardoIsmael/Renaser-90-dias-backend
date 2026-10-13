package com.renaser.os.shared.domain;

/**
 * Un oyente de evento no actúa porque su funcionalidad está apagada y su política es POSPONER (spec 002, catálogo):
 * al lanzarla, la publicación queda incompleta en el outbox de Spring Modulith y se reentrega cada 5 minutos
 * ({@code EventPublicationMaintenanceScheduler}) hasta que la funcionalidad se prenda. Los oyentes que la lanzan son
 * idempotentes, así que la reentrega no duplica nada.
 */
public final class ProcesoPospuestoException extends RuntimeException {

    public ProcesoPospuestoException(String clave, String proceso) {
        super(proceso + ": la funcionalidad " + clave + " está apagada; se pospone hasta que se prenda");
    }
}
