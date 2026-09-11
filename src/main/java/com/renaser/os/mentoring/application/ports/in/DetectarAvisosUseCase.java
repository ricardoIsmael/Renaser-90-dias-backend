package com.renaser.os.mentoring.application.ports.in;

/** Barrido periódico que busca condiciones que el mentor debería mirar. */
public interface DetectarAvisosUseCase {

    /**
     * Recorre los grupos con mentor vigente y publica un evento por cada condición nueva.
     *
     * @return cuántos avisos se publicaron. Los que ya existían no se cuentan: la
     *         deduplicación los descarta río abajo, sin que este barrido tenga que consultarla.
     */
    int detectar();
}
