package com.renaser.os.shared.application;

import com.renaser.os.shared.domain.UserId;

/**
 * Si una funcionalidad está prendida (spec 002; contrato §1). Lo consultan las tareas programadas, los oyentes y los
 * WebSockets de cualquier módulo; los endpoints REST ya los filtra el interceptor de {@code plataforma} por su
 * {@code @Funcionalidad}.
 *
 * <p>Vive en {@code shared} y lo implementa {@code plataforma} (inversión de dependencias): si cada módulo importara
 * {@code plataforma.api}, {@code users -> plataforma -> users} sería un ciclo. Sin {@code plataforma} en el contexto
 * (la prueba de un módulo suelto) responde {@code TodoPrendido}.
 *
 * <p>Consultarlo es leer un mapa en memoria (≤ 2 ms p95, FR-011): no hace falta guardarlo en una variable.
 */
public interface Interruptores {

    /**
     * Estado global, para procesos sin actor (tareas programadas, oyentes): apagada para todos o por su madre. Una
     * excepción por rol no la apaga acá, porque un proceso no tiene rol.
     */
    boolean prendida(String clave);

    /** Estado para una persona: además del global, la excepción del rol que tiene hoy. */
    boolean prendidaPara(String clave, UserId actor);

    /**
     * Lanza {@link com.renaser.os.shared.domain.FuncionalidadApagadaException} con el aviso editable si está apagada
     * para esa persona. Es lo que usa un servicio cuando la funcionalidad depende del recurso (el tipo de una
     * conversación) y no de la ruta.
     */
    void exigirPrendidaPara(String clave, UserId actor);
}
