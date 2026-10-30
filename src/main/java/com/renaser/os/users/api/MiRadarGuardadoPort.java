package com.renaser.os.users.api;

import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Lo guardado de Mi Radar (spec 012 rehecha por la 019, V122): {@code participantes_programa.mi_radar_encendido} y
 * {@code participantes_programa.dias_mi_radar} (por día de la semana, solo los que no son de fábrica). {@code users} es
 * dueño de la tabla, y {@code habits} —que es quien sabe qué vale— no manda SQL contra ella (regla 01, D-41): pasa por acá.
 *
 * <p>Mismo reparto que {@link AsignacionCelulaPort}: {@code users} guarda y devuelve las columnas, y la validación (el día
 * de 1 a 7, el modo, las veces) es de quien llama. Las horas ya no se guardan: se calculan con el Despertar y el Dormir.
 */
public interface MiRadarGuardadoPort {

    /**
     * Lo guardado. Sin nada es apagado y sin días propios.
     *
     * @throws java.util.NoSuchElementException si la persona no tiene fila de programa
     */
    MiRadarGuardado de(UserId participanteId);

    /**
     * Reemplaza lo guardado. Sin días guarda {@code NULL} en {@code dias_mi_radar}.
     *
     * @throws java.util.NoSuchElementException si la persona no tiene fila de programa
     */
    void guardar(UserId participanteId, MiRadarGuardado guardado);

    /** @param dias los días de la semana que no son de fábrica */
    record MiRadarGuardado(boolean encendido, List<DiaGuardado> dias) {

        public MiRadarGuardado {
            dias = List.copyOf(dias);
        }
    }

    /**
     * @param dia      lunes = 1 … domingo = 7
     * @param modo     {@code AUTOMATICO} o {@code PERSONALIZADO}
     * @param veces    solo en Personalizado; {@code null} si no
     * @param sinRadar la persona lo apagó para ese día
     */
    record DiaGuardado(int dia, String modo, Integer veces, boolean sinRadar) {
    }
}
