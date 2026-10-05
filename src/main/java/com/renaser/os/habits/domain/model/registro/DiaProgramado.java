package com.renaser.os.habits.domain.model.registro;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Un dia en que a un habito LE TOCABA (D-254), con como quedo.
 *
 * <p><b>"Le tocaba" es que haya un registro, y nada mas.</b> {@code registros_habito} es el snapshot
 * historico de lo que se le pidio a la persona cada dia: el barrido no genera fila en los dias en que
 * el habito no esta programado (dias de la semana apagados, horario por fecha, desbloqueo que todavia
 * no llego) ni en los dias en pausa, y pausar BORRA lo que quedaba pendiente
 * ({@code RetirarObligacionesPausadasPort}: "un dia pausado tiene que verse igual que un dia en que el
 * habito no estaba programado — que es exactamente una fila que no existe"). Por eso la racha no
 * necesita reconstruir la programacion de cada dia pasado: la programacion de ESE dia ya quedo escrita
 * en que exista o no la fila, aunque hoy el habito tenga otros dias.
 *
 * @param opcional {@code registros_habito.es_opcional}: el dia no era exigible (ciclo de intoxicacion
 *                 o habito opcional del catalogo). Hoy NO cambia la racha — ver
 *                 {@link RachaDelHabito}, supuesto S-1 de D-254. Viaja igual para que esa decision
 *                 quede a la vista y una prueba la fije.
 */
public record DiaProgramado(LocalDate fecha, EstadoRegistro estado, boolean opcional) {

    public DiaProgramado {
        Objects.requireNonNull(fecha, "la fecha del dia programado es obligatoria");
        Objects.requireNonNull(estado, "el estado del dia programado es obligatorio");
    }
}
