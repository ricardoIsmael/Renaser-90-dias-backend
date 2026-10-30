package com.renaser.os.habits.domain.model.radar;

import com.renaser.os.habits.domain.model.radar.DiaDelRadar.MotivoSinHorario;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar.Modo;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

/**
 * Las reglas de Mi Radar (spec 019): qué horas pregunta cada día, cuándo se puede registrar y a qué hora estaba prevista
 * la respuesta. Funciones puras: reciben el Despertar y el Dormir del día, el día del programa y el momento LOCAL de la
 * persona —su zona, no la del servidor: regla 02— y no leen ningún reloj.
 *
 * <p><b>Las horas de un día.</b> Son las horas en punto {@code h} con {@code Despertar ≤ h:00 < Dormir}, recortadas a
 * 03:00–23:00 y sin tope de cantidad: Despertar 05:30 y Dormir 22:30 dan 06 a 22 (17); 08:00 y 23:00, 08 a 22 (15). Sin
 * Despertar, sin Dormir o con Dormir ≤ Despertar el día no tiene Radar y no se inventa ningún horario. En Personalizado se
 * eligen {@code n} de esas horas repartidas parejo: la posición {@code i} de las {@code n} es
 * {@code round(i·(total−1)/(n−1))}, con la mitad hacia arriba y en enteros (6 de 17 dan 06, 09, 12, 16, 19, 22). La app
 * repite esta misma cuenta, igual y con el mismo redondeo, solo para ajustar el − / +.
 *
 * <p><b>Registrar.</b> Los días 0 a 7 son los de siempre: a cualquier hora, y la hora prevista es la local si cae entre las
 * 08 y las 19. Desde el día 8 solo con Mi Radar encendido, en un día que tenga Radar y de 03:00 a 23:59; la hora prevista es
 * la local si es una de las del día. El día 90 no cambia nada.
 */
public final class HorarioDelRadar {

    /** Primer día del programa en que el Código Renaser pasa a ser Mi Radar. */
    public static final int PRIMER_DIA_DE_MI_RADAR = 8;
    public static final int HORA_PRIMERA = 3;
    public static final int HORA_ULTIMA = 23;

    private static final int PRIMERA_HORA_DE_LA_SEMANA_1 = 8;
    private static final int ULTIMA_HORA_DE_LA_SEMANA_1 = 19;

    private HorarioDelRadar() {
    }

    /** Si Mi Radar existe para quien va por este dia: antes del 8 el Codigo Renaser es el de la primera semana. */
    public static boolean aplicaMiRadar(int diaPrograma) {
        return diaPrograma >= PRIMER_DIA_DE_MI_RADAR;
    }

    /**
     * Las horas en punto de 03 a 23 que caben entre el Despertar y el Dormir, de menor a mayor. Vacía si falta alguno o si
     * el Dormir no es después del Despertar.
     */
    public static List<Integer> horasQueCaben(LocalTime despertar, LocalTime dormir) {
        if (despertar == null || dormir == null || !dormir.isAfter(despertar)) {
            return List.of();
        }
        List<Integer> horas = new ArrayList<>();
        for (int hora = HORA_PRIMERA; hora <= HORA_ULTIMA; hora++) {
            LocalTime enPunto = LocalTime.of(hora, 0);
            if (!enPunto.isBefore(despertar) && enPunto.isBefore(dormir)) {
                horas.add(hora);
            }
        }
        return List.copyOf(horas);
    }

    /**
     * {@code n} de estas horas repartidas parejo. Con {@code n} igual o mayor que las que hay salen todas; con {@code n}
     * de 1 o menos, la primera.
     */
    public static List<Integer> repartir(List<Integer> horas, int n) {
        int total = horas.size();
        if (total == 0) {
            return List.of();
        }
        if (n >= total) {
            return List.copyOf(horas);
        }
        if (n <= 1) {
            return List.of(horas.getFirst());
        }
        List<Integer> elegidas = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            // round(i·(total−1)/(n−1)) con la mitad hacia arriba, en enteros: floor((2·num + den) / (2·den)).
            int posicion = (2 * i * (total - 1) + (n - 1)) / (2 * (n - 1));
            elegidas.add(horas.get(posicion));
        }
        return List.copyOf(elegidas);
    }

    /** El día resuelto: sus horas, cuántas caben y, si no hay horario, por qué. */
    public static DiaDelRadar planDelDia(DiaDeMiRadar dia, LocalDate fecha, LocalTime despertar, LocalTime dormir,
                                         boolean editable) {
        List<Integer> caben = horasQueCaben(despertar, dormir);
        MotivoSinHorario motivo = caben.isEmpty() ? motivoDeLaFalta(despertar, dormir) : null;
        List<Integer> horas = dia.sinRadar() || caben.isEmpty() ? List.of()
                : dia.modo() == Modo.AUTOMATICO ? caben : repartir(caben, Math.min(dia.veces(), caben.size()));
        return new DiaDelRadar(dia.dia(), fecha, dia.modo(), caben.size(), horas.size(), dia.sinRadar(), editable,
                despertar, dormir, horas, motivo);
    }

    private static MotivoSinHorario motivoDeLaFalta(LocalTime despertar, LocalTime dormir) {
        if (despertar == null) {
            return MotivoSinHorario.SIN_DESPERTAR;
        }
        return dormir == null ? MotivoSinHorario.SIN_DORMIR : MotivoSinHorario.HORARIO_INVALIDO;
    }

    /**
     * Hoy y lo pasado no se editan. La semana que se muestra va de lunes a domingo: un día se edita si es posterior al de
     * hoy. El domingo se muestra la semana siguiente y de lunes a sábado se edita, pero el domingo mismo no: es una regla por
     * día de la semana sin fecha y cambiarlo cambiaría hoy.
     */
    public static boolean esEditable(DayOfWeek dia, LocalDate hoy) {
        if (hoy.getDayOfWeek() == DayOfWeek.SUNDAY) {
            return dia != DayOfWeek.SUNDAY;
        }
        return dia.getValue() > hoy.getDayOfWeek().getValue();
    }

    /** El lunes de la semana que se muestra: la de hoy o, si hoy es domingo, la siguiente. */
    public static LocalDate lunesDeLaSemanaMostrada(LocalDate hoy) {
        return hoy.getDayOfWeek() == DayOfWeek.SUNDAY ? hoy.plusDays(1)
                : hoy.minusDays(hoy.getDayOfWeek().getValue() - 1L);
    }

    /** La próxima fecha, hoy incluido, en que cae ese día de la semana: la que decide su horario. */
    public static LocalDate proximaFecha(int dia, LocalDate hoy) {
        return hoy.plusDays(Math.floorMod(dia - hoy.getDayOfWeek().getValue(), 7));
    }

    /**
     * {@code local} es el momento en la zona de la persona; {@code horasDeHoy} las que pregunta hoy (vacía si hoy no tiene
     * Radar, o si está apagado).
     */
    public static boolean puedeRegistrar(int diaPrograma, boolean encendido, List<Integer> horasDeHoy,
                                         ZonedDateTime local) {
        if (!aplicaMiRadar(diaPrograma)) {
            return true;
        }
        int hora = local.getHour();
        return encendido && !horasDeHoy.isEmpty() && hora >= HORA_PRIMERA && hora <= HORA_ULTIMA;
    }

    /** Vacío si el registro es libre: no responde a ninguna hora prevista. */
    public static OptionalInt horaPrevista(int diaPrograma, List<Integer> horasDeHoy, ZonedDateTime local) {
        int hora = local.getHour();
        boolean esPrevista = aplicaMiRadar(diaPrograma)
                ? horasDeHoy.contains(hora)
                : hora >= PRIMERA_HORA_DE_LA_SEMANA_1 && hora <= ULTIMA_HORA_DE_LA_SEMANA_1;
        return esPrevista ? OptionalInt.of(hora) : OptionalInt.empty();
    }
}
