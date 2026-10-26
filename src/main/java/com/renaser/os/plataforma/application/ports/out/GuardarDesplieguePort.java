package com.renaser.os.plataforma.application.ports.out;

import com.renaser.os.plataforma.domain.model.despliegue.DespliegueDeFuncionalidad;
import com.renaser.os.shared.domain.UserId;

import java.time.Instant;

/**
 * Escribe los despliegues con bloqueo optimista (contrato fase 2 §3.4: 409 si la versión cambió). Cada método devuelve
 * {@code false} si otro cambio llegó antes y no tocó nada; todo corre en la transacción de quien llama.
 */
public interface GuardarDesplieguePort {

    /** La versión que le toca al próximo escalón: la secuencia global de la plataforma (nunca se repite). */
    long siguienteVersion();

    /** Crea la fila; {@code false} si ya había un despliegue de esa funcionalidad. */
    boolean empezar(DespliegueDeFuncionalidad nuevo);

    /** Cambia el escalón; {@code false} si la fila ya no tiene {@code versionEsperada} (o no existe). */
    boolean cambiarPorcentaje(DespliegueDeFuncionalidad nuevo, long versionEsperada);

    /** «Prender para todos»: borra la fila y sus probadores; {@code false} si la versión cambió. */
    boolean terminar(String clave, long versionEsperada);

    /** {@code false} si ya era probadora o si la funcionalidad dejó de estar en despliegue. */
    boolean sumarProbadora(String clave, UserId persona, UserId por, Instant en);

    /** {@code false} si no era probadora. */
    boolean quitarProbadora(String clave, UserId persona);
}
