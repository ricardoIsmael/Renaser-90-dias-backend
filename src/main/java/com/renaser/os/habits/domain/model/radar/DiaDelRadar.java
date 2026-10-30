package com.renaser.os.habits.domain.model.radar;

import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar.Modo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * Un día de la semana de Mi Radar ya resuelto con el horario de la persona (spec 019): lo que pregunta y por qué.
 *
 * @param dia      lunes = 1 … domingo = 7
 * @param fecha    la fecha de ese día en la semana que se muestra
 * @param maximo   las horas en punto que caben entre su Despertar y su Dormir (0 si falta alguno)
 * @param veces    las que pregunta de verdad ({@code horas.size()}): 0 si el día no tiene Radar
 * @param sinRadar la persona lo apagó para este día
 * @param editable hoy y lo pasado no se editan (FR-005)
 * @param horas    las horas en punto, de menor a mayor; vacía si el día no tiene Radar
 * @param motivo   por qué no hay horario, o {@code null} si lo hay
 */
public record DiaDelRadar(int dia, LocalDate fecha, Modo modo, int maximo, int veces, boolean sinRadar,
                          boolean editable, LocalTime despertar, LocalTime dormir, List<Integer> horas,
                          MotivoSinHorario motivo) {

    public DiaDelRadar {
        horas = List.copyOf(horas);
    }

    /** Por qué un día no tiene horario: sin él no se inventa uno (spec 019, regla 3). */
    public enum MotivoSinHorario {
        SIN_DESPERTAR, SIN_DORMIR, HORARIO_INVALIDO
    }
}
