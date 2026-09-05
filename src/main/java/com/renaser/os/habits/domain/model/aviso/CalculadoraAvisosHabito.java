package com.renaser.os.habits.domain.model.aviso;

import com.renaser.os.habits.domain.model.registro.VentanaEntrega;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Decide QUE avisos corresponde mandar en este instante para una ventana de entrega. Es la
 * regla completa, pura y sin infraestructura: no sabe de participantes, ni de notificaciones,
 * ni de zonas horarias (la ventana ya llega resuelta en la zona del participante).
 *
 * <p><b>Por que "esta en la franja" y no "ya paso el momento".</b> Un aviso se manda solo
 * dentro de la franja {@code [momento - antelacion, momento)}. Alcanzaba con "ya paso el
 * umbral y todavia no vencio" para que la deduplicacion de {@code notificaciones} hiciera el
 * resto, pero eso tiene un efecto feo: si el backend estuvo caido toda la franja y vuelve
 * cuando el habito YA empezo, el aprendiz recibe "tu habito empieza en 0 minutos" un rato
 * despues de que empezo. Es preferible no avisar a avisar algo falso — el aviso es una
 * cortesia, no un registro de auditoria.
 *
 * <p><b>Sobre las dos antelaciones.</b> Son parametros, no constantes: el dueno todavia no
 * confirmo cuantos minutos antes quiere cada aviso, y esta clase no puede inventarlos (regla
 * 00, "no inventar reglas de negocio"). Quien la construye los inyecta desde configuracion
 * ({@code renaser.habits.avisos.*}), de modo que cambiarlos sea un cambio de entorno y no de
 * codigo. Una antelacion de cero deja el aviso apagado, por construccion: la franja
 * {@code [momento, momento)} es vacia.
 *
 * <p><b>Contra que instante se mide cada uno:</b>
 * <ul>
 *   <li>{@link TipoAvisoHabito#INICIO} contra {@link VentanaEntrega#instanteInicio()} — la hora
 *   de disparo del habito ese dia. Nulo (y sin aviso) si el habito solo tiene hora limite.</li>
 *   <li>{@link TipoAvisoHabito#POR_VENCER} contra {@link VentanaEntrega#plazoEvidencia()} — NO
 *   contra la hora limite. Desde D-97 entregar dentro de la extension paga el puntaje COMPLETO,
 *   asi que la hora limite ya no es el momento en que se pierde algo; el plazo si lo es: pasado
 *   el, el registro se bloquea y el puntaje cae a cero. Avisar en la hora limite seria avisar de
 *   una perdida que no existe.</li>
 * </ul>
 */
public record CalculadoraAvisosHabito(Duration antelacionInicio, Duration antelacionVencimiento) {

    public CalculadoraAvisosHabito {
        Objects.requireNonNull(antelacionInicio, "antelacionInicio es obligatoria");
        Objects.requireNonNull(antelacionVencimiento, "antelacionVencimiento es obligatoria");
        if (antelacionInicio.isNegative() || antelacionVencimiento.isNegative()) {
            throw new IllegalArgumentException("las antelaciones de aviso no pueden ser negativas");
        }
    }

    /**
     * @param ventana la ventana ya resuelta del registro, o {@code null} si el habito no tiene
     *                ninguna hora configurada — en ese caso no vence nunca y no hay nada que avisar
     * @return los avisos debidos ahora, entre cero y dos, en orden cronologico
     */
    public List<AvisoHabito> debidosAhora(VentanaEntrega ventana, Instant ahora) {
        if (ventana == null) {
            return List.of();
        }
        List<AvisoHabito> avisos = new ArrayList<>(2);
        AvisoHabito inicio = avisoSiEstaEnFranja(TipoAvisoHabito.INICIO, ventana.instanteInicio(), ahora);
        if (inicio != null) {
            avisos.add(inicio);
        }
        AvisoHabito porVencer = avisoSiEstaEnFranja(TipoAvisoHabito.POR_VENCER, ventana.plazoEvidencia(), ahora);
        if (porVencer != null) {
            avisos.add(porVencer);
        }
        return List.copyOf(avisos);
    }

    /** {@code null} cuando {@code ahora} cae fuera de {@code [momento - antelacion, momento)}. */
    private AvisoHabito avisoSiEstaEnFranja(TipoAvisoHabito tipo, Instant momento, Instant ahora) {
        if (momento == null) {
            return null;
        }
        Duration antelacion = tipo == TipoAvisoHabito.INICIO ? antelacionInicio : antelacionVencimiento;
        if (ahora.isBefore(momento.minus(antelacion)) || !ahora.isBefore(momento)) {
            return null;
        }
        return new AvisoHabito(tipo, momento, Duration.between(ahora, momento));
    }
}
