package com.renaser.os.habits.domain.model.registro;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Objects;

/**
 * E-534 — hasta donde vence lo {@code PENDIENTE} de UN participante: todo dia anterior a su HOY, en SU zona.
 *
 * <p><b>El dia de una persona termina a SU medianoche, no a una hora UTC fija</b> (regla 02 §1, la familia de E-91).
 * Es la misma frontera que ya usa {@link VentanaEntrega}: la extension de un habito se recorta contra la medianoche
 * siguiente en la zona del participante, asi que su {@code plazoEvidencia} nunca promete mas alla de ella. Vencer por
 * el dia local deja el barrido coherente con ese plazo en cualquier zona; para Lima (UTC−5) es exactamente lo que
 * hacia el barrido diario de las 05:00 UTC, que caia justo en su medianoche.
 *
 * <p>Pura y derivada (regla 02 §2): depende solo del instante y de la zona. Correrla dos veces da lo mismo, y
 * correrla tarde —una noche sin servidor— vence de una vez todo lo atrasado.
 *
 * @param hoyEnSuZona la fecha de hoy para el participante; todo dia anterior ya termino
 */
public record CorteDeExpiracion(LocalDate hoyEnSuZona) {

    public CorteDeExpiracion {
        Objects.requireNonNull(hoyEnSuZona, "el hoy del participante es obligatorio");
    }

    /** El corte de un participante de esa zona en ese instante: {@code ahora.atZone(zona).toLocalDate()}. */
    public static CorteDeExpiracion para(ZoneId zona, Instant ahora) {
        Objects.requireNonNull(zona, "la zona del participante es obligatoria");
        return new CorteDeExpiracion(ahora.atZone(zona).toLocalDate());
    }

    /**
     * La fecha local mas tardia que puede ser "hoy" en alguna zona en este instante ({@link ZoneOffset#MAX}). Sirve
     * de tope para buscar candidatos en todo el padron sin conocer la zona de cada uno: nada que pueda haber vencido
     * en alguna zona queda afuera, y lo de una fecha posterior no vencio en ninguna.
     */
    public static LocalDate fechaMasTardiaPosible(Instant ahora) {
        return ahora.atOffset(ZoneOffset.MAX).toLocalDate();
    }

    /** {@code true} si el dia de esa fecha ya termino para el participante. */
    public boolean yaTermino(LocalDate fecha) {
        return fecha.isBefore(hoyEnSuZona);
    }

    /**
     * Lo que el barrido pasa a {@code EXPIRADO}: un registro {@code PENDIENTE} de un dia que ya termino. Un
     * {@code EN_CURSO} no (lo cierra el barrido de las rachas sin celular), ni nada terminal.
     */
    public boolean vence(RegistroHabito registro) {
        return registro.estado() == EstadoRegistro.PENDIENTE && yaTermino(registro.fechaEjecucion());
    }
}
