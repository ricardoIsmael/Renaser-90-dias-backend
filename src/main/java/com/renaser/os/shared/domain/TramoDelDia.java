package com.renaser.os.shared.domain;

import java.time.LocalTime;

/**
 * Un tramo {@code [desde, hasta)} de un día local, en minutos desde la medianoche: de 0 a 1440 (1440 es el fin del día).
 *
 * <p>Es solo aritmética de horas, sin zona ni fecha: quien lo usa ya resolvió de qué día local habla. Lo comparten el
 * acompañante de SER ({@code rag}, horas ocupadas que la persona dice) y los huecos de horario (spec 015), para que
 * «juntar lo que se cruza» y «sacar lo libre» sean UNA sola regla ({@link TramosUnidos}).
 */
public record TramoDelDia(int desde, int hasta) {

    public static final int FIN_DEL_DIA = 24 * 60;

    public TramoDelDia {
        if (desde < 0 || hasta > FIN_DEL_DIA || desde >= hasta) {
            throw new IllegalArgumentException("Tramo invalido: " + desde + "-" + hasta);
        }
    }

    public int minutos() {
        return hasta - desde;
    }

    /** Los minutos desde la medianoche de una hora local. */
    public static int minutosDe(LocalTime hora) {
        return hora.getHour() * 60 + hora.getMinute();
    }

    /** {@code "05:30"}; el fin del día (1440) se dice {@code "24:00"}. */
    public static String hora(int minutos) {
        return "%02d:%02d".formatted(minutos / 60, minutos % 60);
    }
}
