package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Los puntos de liga y la racha de un participante, con la MISMA semantica que muestra
 * {@code GET /api/v1/home} ({@code HomeAgregadoService}), para que otro modulo pueda leerlos sin
 * copiar la regla (2026-09-23, hueco de D-152 en {@code docs/MODULO_RAG.md}).
 *
 * <p><b>La racha es la que se MUESTRA, no la guardada.</b> Se deriva de los dias con al menos un
 * habito cumplido ({@code points.domain.model.puntaje.Racha.derivarDe}), igual que Inicio. No es
 * {@code puntajes_participante.racha_actual} — la que devuelve {@code GET /api/v1/points/{id}} —,
 * que vale 0 para todo el mundo porque nadie la avanza (ver javadoc de
 * {@code HomeAgregadoService.rachaDe}). Las dos pantallas y este contrato pasan por el mismo codigo
 * dentro de {@code points}: la regla esta escrita una sola vez.
 *
 * <p><b>Los puntos de liga</b> son los de {@code ConsultarPuntajeUseCase}: el saldo de la fila de
 * puntaje, o el estado inicial si todavia no tiene fila. El mismo numero que Inicio.
 *
 * <p><b>No recibe {@code actorId}</b>: es una llamada de modulo a modulo sobre la propia persona.
 * Reusa la verificacion de cuenta activa de {@code ConsultarPuntajeUseCase}, asi que una cuenta
 * suspendida propaga {@code NotAuthorizedException} — el caller decide que hacer, este puerto no lo
 * sabe.
 */
public interface ResumenPuntajeFinder {

    /**
     * @param ahora el instante de la consulta; se recibe para que "hoy en la zona del participante"
     *              (regla 02 §1) sea el mismo que usa quien llama
     * @return vacio solo si la persona no existe en {@code users}
     */
    Optional<ResumenPuntaje> de(UserId participanteId, Instant ahora);

    /**
     * @param puntosLiga   saldo de puntos de liga, mismo valor que {@code puntosLiga} de Inicio
     * @param rachaActual  dias seguidos con al menos un habito cumplido, terminando hoy o ayer
     * @param rachaMaxima  el tramo mas largo desde el inicio del programa
     */
    record ResumenPuntaje(int puntosLiga, int rachaActual, int rachaMaxima) {
    }
}
