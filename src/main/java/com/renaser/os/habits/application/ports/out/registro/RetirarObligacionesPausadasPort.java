package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;

/**
 * Retira las obligaciones que quedan sin efecto al pausar un hábito.
 *
 * <p><b>El agujero que cierra.</b> Pausar solo apagaba la generación FUTURA: el track del día ya
 * estaba creado —lo hace el barrido de las 05:02 o la primera apertura de la app— y ahí se
 * quedaba, en PENDIENTE. La persona apagaba el hábito a las 10:00, lo seguía viendo en su día y
 * en evidencias, y a la noche el barrido lo marcaba fallado. El botón decía "solo hoy" y hoy te
 * contaba igual.
 *
 * <p><b>Solo lo que sigue ABIERTO.</b> Un COMPLETADO se queda: lo hiciste, es tuyo. Un FALLIDO o
 * un EXPIRADO también, y esto importa más de lo que parece — si pausar borrara lo ya vencido,
 * cualquiera limpiaría sus fallos pausando después de fallar. Es la misma regla que ya sostiene
 * {@code DesbloqueoHabito.estaPausadoEl}: una pausa vale desde que la tocas, nunca hacia atrás.
 */
public interface RetirarObligacionesPausadasPort {

    /**
     * Borra los registros PENDIENTE de ese hábito entre {@code desde} y {@code hasta}, ambos
     * inclusive. {@code hasta} nulo = pausa indefinida, sin tope.
     *
     * <p>Se BORRA en vez de marcarse: no existe un estado "cancelado", y no debería. Un día
     * pausado tiene que verse igual que un día en que el hábito no estaba programado — que es
     * exactamente una fila que no existe. Inventar un quinto estado obligaría a enseñarle a la
     * fórmula de cumplimiento, al barrido nocturno y a la rejilla del mentor a ignorarlo, y el
     * día que uno de los tres se olvidara, el día pausado contaría como incumplido.
     *
     * @return cuántas se retiraron.
     */
    int retirarPendientes(UserId participanteId, HabitoId habitoId, LocalDate desde, LocalDate hasta);
}
