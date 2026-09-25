package com.renaser.os.mentoring.domain.model.semaforo;

import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Objects;

/**
 * Las fechas que encabezan una vista de grupo del semáforo: desde, hasta y si ese período ya
 * cerró (su resultado ya no cambia).
 *
 * <p>Cada aprendiz trae su propia ventana, calculada en SU zona horaria. El encabezado sale de esas
 * ventanas cuando coinciden en sus fechas, que es lo normal: todo el padrón vive en America/Lima.
 * Si no hay ninguna —nadie del grupo se mide— o no coinciden —aprendices en zonas distintas cerca
 * de la medianoche—, las fechas salen del período {@link #esperado}, calculado en la zona del grupo.
 */
public record PeriodoDelSemaforo(LocalDate desde, LocalDate hasta, boolean cerrada) {

    public PeriodoDelSemaforo {
        Objects.requireNonNull(desde, "desde es obligatorio");
        Objects.requireNonNull(hasta, "hasta es obligatorio");
        if (desde.isAfter(hasta)) {
            throw new IllegalArgumentException("desde posterior a hasta: " + desde + " > " + hasta);
        }
    }

    /**
     * El período que corresponde a un día, sin mirar a nadie.
     *
     * @param hoyLocal    hoy en la zona de referencia, resuelto por quien llama con
     *                    {@code clock.now().atZone(zona)}: nunca la fecha del servidor (regla 02)
     * @param semanaHasta null = la ventana vigente, los 7 días que terminan ayer; si no, la semana
     *                    sábado→viernes que termina ese viernes
     */
    public static PeriodoDelSemaforo esperado(LocalDate hoyLocal, LocalDate semanaHasta) {
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
        LocalDate hasta = semanaHasta != null ? semanaHasta : hoyLocal.minusDays(1);
        LocalDate desde = semanaHasta != null
                ? SemanaDelSemaforo.desde(semanaHasta)
                : hasta.minusDays(SemanaDelSemaforo.DIAS - 1L);
        // Cerrado = su último día ya cae dentro de una semana cerrada al llegar hoy.
        boolean cerrada = !hasta.isAfter(SemanaDelSemaforo.ultimaCerradaAl(hoyLocal));
        return new PeriodoDelSemaforo(desde, hasta, cerrada);
    }

    /**
     * El período de las ventanas leídas: sus fechas si coinciden, las de {@code esperado} si no;
     * cerrado solo si TODAS están cerradas. Sin ninguna ventana, {@code esperado} tal cual.
     */
    public static PeriodoDelSemaforo de(Collection<VentanaDelSemaforo> ventanas, PeriodoDelSemaforo esperado) {
        Objects.requireNonNull(esperado, "esperado es obligatorio");
        if (ventanas.isEmpty()) {
            return esperado;
        }
        VentanaDelSemaforo primera = ventanas.iterator().next();
        boolean coinciden = ventanas.stream()
                .allMatch(v -> v.desde().equals(primera.desde()) && v.hasta().equals(primera.hasta()));
        boolean cerrada = ventanas.stream().allMatch(VentanaDelSemaforo::cerrada);
        return coinciden
                ? new PeriodoDelSemaforo(primera.desde(), primera.hasta(), cerrada)
                : new PeriodoDelSemaforo(esperado.desde(), esperado.hasta(), cerrada);
    }
}
