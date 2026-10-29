package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Las horas de Mi Radar (spec 012, V119): {@code participantes_programa.horas_mi_radar} (las de todos los días) y
 * {@code participantes_programa.dias_mi_radar} (los días de la semana con horas propias), las dos columnas donde la
 * persona guarda a qué horas el Código Renaser le pregunta desde el día 8. {@code users} es dueño de la tabla, y
 * {@code habits} —que es quien sabe qué horas valen— no manda SQL contra ella (regla 01, D-41): pasa por acá.
 *
 * <p>Mismo reparto que {@link AsignacionCelulaPort}: {@code users} guarda y devuelve las columnas, y la validación de
 * las horas y los días (de 1 a 12, en punto, de 03 a 23, sin repetir; día de 1 a 7, sin repetir) es de quien llama.
 * Nada de lo que se guarda acá se loguea.
 */
public interface HorasDeMiRadarPort {

    /**
     * Lo guardado, en el orden en que se guardó. Sin horas ({@code NULL} en la base) es Mi Radar apagado y sin días.
     *
     * @throws java.util.NoSuchElementException si la persona no tiene fila de programa
     */
    HorasDeMiRadar horasDe(UserId participanteId);

    /**
     * Reemplaza lo guardado. Sin horas apaga Mi Radar y guarda {@code NULL} en las dos columnas: apagado no deja horas
     * ni días viejos esperando. Sin días guarda {@code NULL} en {@code dias_mi_radar}.
     *
     * @throws java.util.NoSuchElementException si la persona no tiene fila de programa
     */
    void guardarHoras(UserId participanteId, HorasDeMiRadar horas);

    /**
     * @param horas las de todos los días; vacía = Mi Radar apagado
     * @param dias  los días de la semana con horas propias; un día con {@code horas} vacías es «Sin Radar ese día»
     */
    record HorasDeMiRadar(List<Integer> horas, List<HorasDeUnDia> dias) {

        public HorasDeMiRadar {
            horas = List.copyOf(horas);
            dias = List.copyOf(dias);
        }

        public static HorasDeMiRadar apagado() {
            return new HorasDeMiRadar(List.of(), List.of());
        }
    }

    /** @param dia lunes = 1 … domingo = 7 */
    record HorasDeUnDia(int dia, List<Integer> horas) {

        public HorasDeUnDia {
            horas = List.copyOf(horas);
        }
    }
}
