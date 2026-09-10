package com.renaser.os.community.domain.model.celula;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Desde cuándo y hasta cuándo vive un grupo que armó el administrador.
 *
 * <p><b>No confundir con {@code PeriodoAsignacion}.</b> Los dos se llaman "periodo" y son cosas
 * distintas a propósito:
 *
 * <table>
 *   <tr><th></th><th>{@code PeriodoAsignacion}</th><th>{@code PeriodoGrupo} (este)</th></tr>
 *   <tr><td>Mide</td><td>instantes</td><td>días del calendario</td></tr>
 *   <tr><td>Extremos</td><td>{@code [inicio, fin)} — el fin NO entra</td>
 *       <td>{@code [inicio, fin]} — el último día SÍ entra</td></tr>
 *   <tr><td>Responde</td><td>quién acompañaba en tal momento</td><td>si el grupo sigue vivo hoy</td></tr>
 * </table>
 *
 * <p>El fin inclusivo no es un descuido: lo que el administrador escribe es "del 1 al 30", y el 30
 * es un día de grupo entero. Con el otro convenio, un grupo "hasta el 30" moriría el 29 por la
 * noche y nadie entendería por qué. La exclusividad de {@code PeriodoAsignacion} existe por el
 * motivo contrario —que un relevo pertenezca a un solo mentor— y ahí sí hace falta.
 *
 * <p>Se compara por DÍA y no por instante, y el día tiene que venir en la zona de quien mira:
 * a las 02:00 UTC del día 1 en Lima todavía es el último día del mes anterior (E-91).
 */
public record PeriodoGrupo(LocalDate inicio, LocalDate fin) {

    /** Lo que el cliente pidió para el grupo de bienvenida. Se nombra para que no ande suelto. */
    public static final int DIAS_DE_RECEPCION = 7;

    public PeriodoGrupo {
        Objects.requireNonNull(inicio, "inicio es obligatorio");
        Objects.requireNonNull(fin, "fin es obligatorio");
        if (fin.isBefore(inicio)) {
            throw new IllegalArgumentException("El periodo termina antes de empezar: " + inicio + " → " + fin);
        }
    }

    /** Un periodo de N días contados desde {@code inicio}, con el primero incluido. */
    public static PeriodoGrupo desde(LocalDate inicio, int dias) {
        if (dias < 1) {
            throw new IllegalArgumentException("Un grupo dura al menos un dia: " + dias);
        }
        // `dias - 1` porque el primer día ya cuenta. Sin el ajuste, "7 días desde el lunes"
        // terminaría el lunes siguiente y el grupo duraría ocho.
        return new PeriodoGrupo(inicio, inicio.plusDays(dias - 1L));
    }

    /** El de bienvenida: siete días desde que arranca. */
    public static PeriodoGrupo recepcionDesde(LocalDate inicio) {
        return desde(inicio, DIAS_DE_RECEPCION);
    }

    /** Del primero al último día del mes de {@code cualquierDiaDelMes}. */
    public static PeriodoGrupo mesDe(LocalDate cualquierDiaDelMes) {
        LocalDate primero = cualquierDiaDelMes.withDayOfMonth(1);
        // `lengthOfMonth` y no 30: febrero existe, y los años bisiestos también.
        return new PeriodoGrupo(primero, primero.withDayOfMonth(cualquierDiaDelMes.lengthOfMonth()));
    }

    /** Si ese día el grupo está vivo. Los dos extremos cuentan. */
    public boolean contiene(LocalDate dia) {
        return !dia.isBefore(inicio) && !dia.isAfter(fin);
    }

    /** Si el grupo ya cerró: el día es POSTERIOR al último. El propio último día no está vencido. */
    public boolean vencidoEn(LocalDate dia) {
        return dia.isAfter(fin);
    }

    /** Si todavía no empezó. Un grupo programado para octubre no es un grupo vencido. */
    public boolean futuroEn(LocalDate dia) {
        return dia.isBefore(inicio);
    }

    /**
     * Cuántos días quedan hasta el cierre, contando el día de hoy como uno. Negativo si ya venció.
     *
     * <p>Es lo que decide el aviso al administrador: "a este grupo le quedan 3 días". Contar hoy
     * importa — con la cuenta cruda, el último día del grupo daría "0 días" y el aviso sonaría a
     * que ya se acabó cuando todavía queda la jornada entera.
     */
    public long diasRestantesEn(LocalDate dia) {
        return java.time.temporal.ChronoUnit.DAYS.between(dia, fin) + 1;
    }

    public int duracionEnDias() {
        return (int) java.time.temporal.ChronoUnit.DAYS.between(inicio, fin) + 1;
    }
}
