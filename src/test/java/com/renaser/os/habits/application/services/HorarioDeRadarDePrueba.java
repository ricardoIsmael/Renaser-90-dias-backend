package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.api.HorarioDelDiaFinder.CuotaCambiosHorario;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorarioResuelto;
import com.renaser.os.habits.api.HorarioDelDiaFinder.HorariosDelDia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BiFunction;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * El Despertar y el Dormir de la persona para las pruebas de Mi Radar (spec 019): arma lo que contestaria
 * {@link HorarioDelDiaFinder}. Un habito de sistema ({@code WAKE_UP} / {@code SLEEP}) por cada hora que no sea nula.
 */
final class HorarioDeRadarDePrueba {

    private HorarioDeRadarDePrueba() {
    }

    static HorarioResuelto habito(String claveSistema, LocalTime hora, boolean apagado, boolean pausado) {
        return new HorarioResuelto(UUID.randomUUID(), claveSistema, hora, null, true, apagado, pausado, false, null,
                false, claveSistema);
    }

    static HorariosDelDia delDia(LocalDate fecha, LocalTime despertar, LocalTime dormir) {
        List<HorarioResuelto> habitos = new ArrayList<>();
        habitos.add(habito("OTRO", LocalTime.of(12, 0), false, false));
        if (despertar != null) {
            habitos.add(habito("WAKE_UP", despertar, false, false));
        }
        if (dormir != null) {
            habitos.add(habito("SLEEP", dormir, false, false));
        }
        return new HorariosDelDia(fecha, 10, habitos, new CuotaCambiosHorario(0, 3, 3, false));
    }

    /** Todos los dias con el mismo Despertar y el mismo Dormir. */
    static void siempre(HorarioDelDiaFinder finder, LocalTime despertar, LocalTime dormir) {
        porFecha(finder, (fecha, dia) -> delDia(fecha, despertar, dormir));
    }

    /** Un horario distinto segun la fecha que se pregunta. */
    static void porFecha(HorarioDelDiaFinder finder, BiFunction<LocalDate, Integer, HorariosDelDia> segun) {
        // doAnswer y no when(...): volver a poner un `when` sobre un mock ya preparado llama a la respuesta anterior con nulos.
        lenient().doAnswer(invocacion -> {
            LocalDate fecha = invocacion.getArgument(1);
            return segun.apply(fecha, fecha.getDayOfWeek().getValue());
        }).when(finder).deFecha(any(), any());
    }
}
