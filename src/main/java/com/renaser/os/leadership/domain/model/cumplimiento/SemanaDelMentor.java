package com.renaser.os.leadership.domain.model.cumplimiento;

import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.shared.domain.RechazoConCodigoException;

import java.time.LocalDate;
import java.util.Objects;

/**
 * La semana que el MENTOR mira en la ficha de su aprendiz (spec 055, FR-011, FR-015 y FR-017): de sábado a viernes, como la
 * del semáforo, pero —a diferencia de la del líder— también la que está en curso, con sus días por venir todavía vacíos.
 *
 * <p>Sin fecha es la semana de HOY. Con fecha tiene que ser un viernes (el cierre de la semana) y la semana tiene que
 * caber entre el Día 1 del aprendiz y hoy. El «hoy» es el de la persona, no el del servidor (regla 02).
 */
public final class SemanaDelMentor {

    public static final String SEMANA_INVALIDA = "SEMANA_INVALIDA";
    public static final String FUERA_DEL_PROGRAMA = "FUERA_DEL_PROGRAMA";

    private SemanaDelMentor() {
    }

    /**
     * @param semanaHasta el viernes que cierra la semana; null = la semana en curso
     * @param hoyLocal    hoy en la zona del aprendiz
     * @param diaUno      el Día 1 de su programa; null si todavía no lo eligió (solo se ve la semana en curso)
     * @throws RechazoConCodigoException {@code SEMANA_INVALIDA} si no es viernes; {@code FUERA_DEL_PROGRAMA} si es una
     *                                   semana futura o anterior a la del Día 1
     */
    public static VentanaDeLaFicha ventana(LocalDate semanaHasta, LocalDate hoyLocal, LocalDate diaUno) {
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
        LocalDate hasta = semanaHasta == null ? SemanaDelSemaforo.cierreDe(hoyLocal) : semanaHasta;
        if (!SemanaDelSemaforo.esCierreValido(hasta)) {
            throw RechazoConCodigoException.invalido(SEMANA_INVALIDA, "La semana termina en viernes: " + hasta + ".");
        }
        LocalDate desde = SemanaDelSemaforo.desde(hasta);
        if (desde.isAfter(hoyLocal)) {
            throw RechazoConCodigoException.invalido(FUERA_DEL_PROGRAMA, "Esa semana todavía no llegó.");
        }
        if (diaUno != null && hasta.isBefore(VentanaDeLaFicha.sabadoQueAbreLaSemanaDe(diaUno).plusDays(6))) {
            throw RechazoConCodigoException.invalido(FUERA_DEL_PROGRAMA, "Esa semana es anterior al Día 1 del programa.");
        }
        if (diaUno == null && hasta.isBefore(SemanaDelSemaforo.cierreDe(hoyLocal))) {
            throw RechazoConCodigoException.invalido(FUERA_DEL_PROGRAMA, "Todavía no eligió su Día 1: solo tiene la semana en curso.");
        }
        boolean cerrada = !hasta.isAfter(SemanaDelSemaforo.ultimaCerradaAl(hoyLocal));
        return new VentanaDeLaFicha(desde, hasta, false, cerrada, hoyLocal);
    }
}
