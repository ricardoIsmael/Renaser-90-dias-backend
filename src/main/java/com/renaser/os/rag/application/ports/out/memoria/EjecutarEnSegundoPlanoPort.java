package com.renaser.os.rag.application.ports.out.memoria;

/**
 * Correr algo despues de responder, sin demorar la respuesta ni ocupar su hilo (D-167). La
 * compactacion llama al modelo: puede tardar segundos y no puede ir dentro de una transaccion (C-1).
 * El adaptador usa hilos virtuales.
 */
public interface EjecutarEnSegundoPlanoPort {

    void ejecutar(Runnable tarea);
}
