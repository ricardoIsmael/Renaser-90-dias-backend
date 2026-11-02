package com.renaser.os.users.api;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Optional;

/**
 * Las fechas del programa de una persona tal como se le muestran en «Mi aceptación» del Acuerdo de transformación (spec 039): cuándo empieza y cuándo termina.
 * Lo necesita {@code onboarding} para armar el acuerdo sin leer {@code participantes_programa} de frente (regla 01, D-41) ni repetir la regla de «mañana» de la
 * spec 036 ni la cuenta del último día: las dos salen del agregado {@code ParticipacionPrograma}.
 *
 * <p>Es una interfaz aparte (y no un método más de {@link ParticipacionProgramaFinder}) por la misma razón que {@link ProgramasActivadosFinder}: aquella tiene
 * implementaciones de prueba en cinco módulos y una pregunta nueva las rompería.
 */
public interface FechasDelProgramaFinder {

    /** Vacío si la persona no existe, está suspendida o no tiene fila en el programa. */
    Optional<FechasDelPrograma> de(UserId persona);

    /**
     * @param formacion    la formación en la que hace su programa
     * @param duracionDias los días de SU programa (90 o 365, spec 022)
     * @param activado     si ya activó su programa (empezó o tiene fecha de inicio elegida)
     * @param inicio       el que tiene si activó; si no, el que tendrá al activar (mañana en su zona, spec 036); {@code null} si no hay un programa que prometerle:
     *                     el personal que todavía no activó el suyo (el de «hacer mi programa» es opcional)
     * @param fin          el último día de su programa; {@code null} cuando {@code inicio} lo es
     */
    record FechasDelPrograma(Formacion formacion, int duracionDias, boolean activado, LocalDate inicio, LocalDate fin) {
    }
}
