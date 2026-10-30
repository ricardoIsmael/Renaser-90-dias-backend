package com.renaser.os.mentoring.domain.model.huecos;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

/**
 * Un hábito o una acción del día que la persona tenía que cumplir, visto desde «lo real» (FR-014 y FR-017): ¿lo cumplió
 * a tiempo, tarde, o no?
 *
 * <p><b>A tiempo</b> es cumplirlo hasta su ancla: la hora de fin (el plazo del hábito) si la tiene y, si no, la de
 * inicio. Sin ninguna hora, como Despertar (D-97), siempre está a tiempo. Todo cumplimiento posterior es «tarde»,
 * incluido el de otro día.
 *
 * @param inicio    la hora de inicio del hábito (disparo) o de la acción; {@code null} si no tiene
 * @param fin       el plazo (límite) del hábito o la hora de fin de la acción; {@code null} si no tiene
 * @param cumplidoEn el instante en que se cumplió; {@code null} si no se cumplió
 * @param opcional  ese día no era exigible: sin cumplirse, no cuenta como falta
 */
public record Cumplible(LocalDate fecha, LocalTime inicio, LocalTime fin, Instant cumplidoEn, boolean opcional) {

    public boolean cumplido() {
        return cumplidoEn != null;
    }

    /** {@code true} si se cumplió hasta su ancla, en la hora local de la persona. */
    public boolean aTiempo(ZoneId zona) {
        LocalTime ancla = fin != null ? fin : inicio;
        if (ancla == null) {
            return true;
        }
        LocalDateTime limite = fecha.atTime(ancla);
        // Un plazo que no es posterior al inicio cierra al día siguiente (22:00 → 02:00), como la ventana de entrega.
        if (fin != null && inicio != null && !inicio.isBefore(fin)) {
            limite = limite.plusDays(1);
        }
        return !cumplidoEn.atZone(zona).toLocalDateTime().isAfter(limite);
    }
}
