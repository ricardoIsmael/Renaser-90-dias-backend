package com.renaser.os.leadership.domain.model.documento;

import com.renaser.os.leadership.domain.model.cumplimiento.SemanaDelMentor;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.shared.domain.RechazoConCodigoException;

import java.time.LocalDate;
import java.util.Objects;

/**
 * La semana de un documento de mentor (spec 056, FR-036): de sábado a viernes, la del semáforo, y también la que está en curso. A diferencia
 * de la semana propia de una persona ({@link SemanaDelMentor}) no tiene tope hacia atrás en un Día 1: lo que el documento cuenta es el
 * grupo del mentor, que existe aunque él no siga su programa.
 *
 * <p>Sin fecha es la semana de HOY; con fecha tiene que ser un viernes y su sábado no puede ser futuro. El «hoy» es el de la persona (regla 02).
 */
public final class SemanaDelDocumento {

    private SemanaDelDocumento() {
    }

    /**
     * @param semanaHasta el viernes que cierra la semana; null = la semana en curso
     * @param hoyLocal    hoy en la zona de la persona de quien es el documento
     * @return el viernes que cierra la semana
     * @throws RechazoConCodigoException {@code SEMANA_INVALIDA} si no es viernes; {@code FUERA_DEL_PROGRAMA} si es una semana que todavía no llegó
     */
    public static LocalDate viernes(LocalDate semanaHasta, LocalDate hoyLocal) {
        Objects.requireNonNull(hoyLocal, "hoyLocal es obligatorio");
        LocalDate hasta = semanaHasta == null ? SemanaDelSemaforo.cierreDe(hoyLocal) : semanaHasta;
        if (!SemanaDelSemaforo.esCierreValido(hasta)) {
            throw RechazoConCodigoException.invalido(SemanaDelMentor.SEMANA_INVALIDA, "La semana termina en viernes: " + hasta + ".");
        }
        if (SemanaDelSemaforo.desde(hasta).isAfter(hoyLocal)) {
            throw RechazoConCodigoException.invalido(SemanaDelMentor.FUERA_DEL_PROGRAMA, "Esa semana todavía no llegó.");
        }
        return hasta;
    }
}
