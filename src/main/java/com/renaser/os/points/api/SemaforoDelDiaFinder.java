package com.renaser.os.points.api;

import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;

/**
 * Un día del semáforo calculado EN VIVO, aunque todavía no haya cerrado (D-223): la tarjeta diaria que el
 * programa manda al chat de soporte a las 23:50 de cada aprendiz ({@code chat}).
 *
 * <p><b>La misma regla que el barrido, no otra.</b> El porcentaje, el color y los estados salen de
 * {@code CumplimientoDelDia} y {@code CalendarioDeMedicion}, los mismos objetos con los que el barrido
 * horario guarda el día en {@code semaforo_dias} al cerrarlo: hábitos + objetivos del día, opcionales no
 * cumplidos afuera, redondeo a entero y cortes 80/60. Lo único distinto es CUÁNDO se mira: acá, con lo
 * que {@code habits} y {@code rocks} reportan en este momento; lo que se complete después (hasta el cierre
 * del sábado) lo toma el barrido, no esta lectura.
 *
 * <p>Una interfaz aparte y no un método más de {@link SemaforoFinder} por el mismo motivo que
 * {@code users.api.ProgramasActivadosFinder}: aquella tiene dobles de prueba en otro módulo que esta
 * pregunta rompería sin que ninguno la haga. No autoriza, igual que {@link SemaforoFinder}.
 */
public interface SemaforoDelDiaFinder {

    /**
     * @param participantes una colección vacía devuelve un mapa vacío sin consultar la base
     * @param fecha         el día LOCAL de esas personas (quien llama lo sacó de la zona de cada una)
     * @return por persona: {@code MEDIDO} con porcentaje y color, o {@code SIN_DATOS} (nada programado),
     *         {@code FUERA_DEL_PROGRAMA} (antes del Día 1 o después del 90), {@code PAUSADO} o
     *         {@code CUENTA_SUSPENDIDA} (D-209). Sin clave = sin programa activado.
     */
    Map<UserId, DiaDelSemaforo> delDia(Collection<UserId> participantes, LocalDate fecha);
}
