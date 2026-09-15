package com.renaser.os.points.domain.model.puntaje;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;
import java.util.TreeSet;

/**
 * La racha del aprendiz: cuántos días seguidos cumplió al menos un hábito.
 *
 * <p><b>La regla, tal como la definió el dueño del producto (2026-09-14):</b> un día cuenta si la
 * persona cumplió <b>al menos un hábito</b> ese día. No importa si cumplió uno o los siete — es
 * <b>por día, no por hábito</b>. Fue explícito al pedirlo: <i>"es por día, no vamos a acumular o
 * salir un bug por hábito cumplido"</i>.
 *
 * <h2>Por qué se DERIVA y no se acumula</h2>
 *
 * <p>Este objeto no guarda nada ni se incrementa: recibe las fechas en que hubo actividad y
 * calcula. Es lo que exige {@code .claude/rules/02} §2, y no es una preferencia de estilo — la
 * regla existe por un bug real (E-91):
 *
 * <blockquote>Un contador que se incrementa desde un cron <b>pierde para siempre</b> cualquier
 * corrida que no ocurra: una noche con el backend caído deja al aprendiz un día atrasado el resto
 * del programa.</blockquote>
 *
 * <p>Derivada, la racha es idempotente gratis: calcularla dos veces da lo mismo, calcularla tarde
 * se pone al día sola, y una noche sin servidor no deja secuela. Eso vale más que cualquier
 * {@code @SchedulerLock}.
 *
 * <p><b>Esto NO reemplaza a {@link PuntajeParticipante#actualizarRachaTrasDia}</b>, que sigue
 * intacto y sigue gobernando el bono de puntos. Aquel método vive de {@code
 * RegistrarCoherenciaDiariaUseCase}, que hoy <b>no tiene ningún llamador</b> en todo el backend —
 * por eso la racha guardada vale 0 para todo el mundo. Este objeto responde <i>qué se muestra</i>;
 * el otro, <i>qué se premia</i>. Mezclarlos habría hecho que empezaran a otorgarse puntos que hoy
 * no se otorgan, como efecto colateral de un cambio de pantalla.
 *
 * <h2>El día en curso no rompe la racha</h2>
 *
 * <p>Si hoy todavía no hay ningún hábito cumplido, la racha se cuenta desde <b>ayer</b>. Sin esto,
 * a las 7 de la mañana todo el padrón vería su racha en 0 y volvería a subir por la tarde: el
 * número parpadearía todos los días y dejaría de significar nada. El día en curso suma cuando se
 * cumple, no resta mientras está abierto.
 */
public record Racha(int actual, int maxima) {

    public static final Racha NINGUNA = new Racha(0, 0);

    public Racha {
        if (actual < 0 || maxima < 0) {
            throw new IllegalArgumentException("Una racha no puede ser negativa: actual=" + actual + ", maxima=" + maxima);
        }
        if (actual > maxima) {
            throw new IllegalArgumentException("La racha actual (" + actual + ") no puede superar a la máxima (" + maxima + ")");
        }
    }

    /**
     * Deriva la racha a partir de los días en que hubo al menos un hábito cumplido.
     *
     * @param diasCumplidos fechas <b>locales del participante</b> — quien llama ya resolvió la zona
     *                      horaria. Se aceptan repetidas y desordenadas: acá se normalizan, porque
     *                      "dos hábitos el martes" es un solo día de racha.
     * @param hoy           la fecha de hoy <b>en la zona del participante</b>, nunca la del
     *                      servidor ({@code .claude/rules/02} §1).
     */
    public static Racha derivarDe(Collection<LocalDate> diasCumplidos, LocalDate hoy) {
        Objects.requireNonNull(hoy, "hoy es obligatorio");
        if (diasCumplidos == null || diasCumplidos.isEmpty()) {
            return NINGUNA;
        }

        // TreeSet: ordena y deduplica de una vez. Lo segundo es la regla "por día, no por hábito".
        TreeSet<LocalDate> dias = new TreeSet<>(diasCumplidos);
        dias.removeIf(dia -> dia == null || dia.isAfter(hoy)); // una fecha futura sería un dato corrupto, no una racha
        if (dias.isEmpty()) {
            return NINGUNA;
        }

        return new Racha(rachaVigenteA(dias, hoy), rachaMasLarga(dias));
    }

    /** Días seguidos que terminan hoy o ayer. Cualquier otro final significa que la racha se cortó. */
    private static int rachaVigenteA(TreeSet<LocalDate> dias, LocalDate hoy) {
        LocalDate ultimo = dias.last();
        if (ultimo.isBefore(hoy.minusDays(1))) {
            return 0;
        }
        int seguidos = 0;
        for (LocalDate dia = ultimo; dias.contains(dia); dia = dia.minusDays(1)) {
            seguidos++;
        }
        return seguidos;
    }

    /** El tramo consecutivo más largo de toda la historia del participante. */
    private static int rachaMasLarga(TreeSet<LocalDate> dias) {
        int mejor = 0;
        int corriente = 0;
        LocalDate anterior = null;
        for (LocalDate dia : dias) {
            corriente = (anterior != null && dia.equals(anterior.plusDays(1))) ? corriente + 1 : 1;
            mejor = Math.max(mejor, corriente);
            anterior = dia;
        }
        return mejor;
    }
}
