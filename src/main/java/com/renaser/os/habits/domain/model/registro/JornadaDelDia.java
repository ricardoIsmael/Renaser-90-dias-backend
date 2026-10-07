package com.renaser.os.habits.domain.model.registro;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * Etapa 1 de zonas (E-556) — el dia de UN participante en un instante: que fecha es para el y a que altura de ese dia
 * estamos, ambas en SU zona.
 *
 * <p><b>El dia de una persona empieza a SU medianoche, no a una hora UTC fija</b> (regla 02 §1, la familia de E-91).
 * El barrido que arma el dia corria a las 05:02 UTC, que es la medianoche de Lima y de nadie mas: alguien en Los
 * Angeles recibia su dia a las 21:02 de la vispera y alguien en Tokio a las 14:02 del dia mismo. Ahora el barrido corre
 * cada hora y esta clase decide con quien hay algo que hacer. Hermana de {@link CorteDeExpiracion} (E-534): aquella
 * dice cuando TERMINA el dia, esta cuando EMPIEZA y que tan temprano se llego a el.
 *
 * <p>Pura y derivada (regla 02 §2): depende solo del instante y de la zona. Correrla dos veces da lo mismo, y correrla
 * tarde da la fecha correcta de ese momento.
 *
 * @param hoy       la fecha de hoy para el participante
 * @param horaLocal la hora que es en su zona
 */
public record JornadaDelDia(LocalDate hoy, LocalTime horaLocal) {

    /**
     * Hasta que hora local el dia se considera "recien empezado": el barrido que llega dentro de este margen arma la
     * jornada COMPLETA, porque el participante todavia no pudo haber perdido ninguna ventana. Son dos horas y no una
     * para absorber un cambio de hora que se salte la medianoche (la primera hora local de ese dia ya es la 01:00).
     */
    static final LocalTime FIN_DEL_AMANECER = LocalTime.of(2, 0);

    public JornadaDelDia {
        Objects.requireNonNull(hoy, "el hoy del participante es obligatorio");
        Objects.requireNonNull(horaLocal, "la hora local del participante es obligatoria");
    }

    /** La jornada de un participante de esa zona en ese instante. */
    public static JornadaDelDia de(ZoneId zona, Instant ahora) {
        Objects.requireNonNull(zona, "la zona del participante es obligatoria");
        ZonedDateTime enSuZona = ahora.atZone(zona);
        return new JornadaDelDia(enSuZona.toLocalDate(), enSuZona.toLocalTime());
    }

    /**
     * {@code true} si el dia acaba de empezar para el participante: se le arma entero. Si no (un barrido atrasado, una
     * noche con el backend caido), se le arma como cuando abre la app ({@code generarDisponiblesAhora}): desde D-259
     * (2026-10-06) tambien entero, salvo en su primer dia del programa.
     *
     * <p><b>Corregido 2026-10-06 (D-259).</b> Decia que en un barrido atrasado se armaba solo «lo que todavia puede
     * completar: generarle ventanas que ya cerraron seria dejarlo con habitos perdidos». Con la regla del dueño un
     * habito se registra durante su dia aunque se le haya pasado la hora, asi que no generarlo era lo que lo perdia.
     */
    public boolean acabaDeEmpezar() {
        return horaLocal.isBefore(FIN_DEL_AMANECER);
    }
}
