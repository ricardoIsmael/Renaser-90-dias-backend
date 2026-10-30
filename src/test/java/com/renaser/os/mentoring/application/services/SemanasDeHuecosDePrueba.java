package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.domain.model.huecos.DiaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.HabitoConHora;
import com.renaser.os.mentoring.domain.model.huecos.HuecoQueSeRepite;
import com.renaser.os.mentoring.domain.model.huecos.ItemPlanificado;
import com.renaser.os.mentoring.domain.model.huecos.LoReal;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.VentanaDelDia;
import com.renaser.os.shared.domain.TramoDelDia;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/** Semanas de huecos ya calculadas, para las pruebas de quien las pide, las guarda o las muestra. */
public final class SemanasDeHuecosDePrueba {

    private SemanasDeHuecosDePrueba() {
    }

    /**
     * La semana del contrato: el lunes con ventana 05:30-22:30, 410 minutos ocupados y un hueco de 15:00 a 17:00; el
     * resto, dias en blanco. {@code real} es 32 a tiempo, 6 tarde y 4 no cumplidos.
     */
    public static SemanaDeHuecos delContrato(LocalDate lunes) {
        List<DiaDeHuecos> dias = new ArrayList<>();
        dias.add(new DiaDeHuecos(lunes, new VentanaDelDia(330, 1350), List.of(new TramoDelDia(330, 740)),
                List.of(new TramoDelDia(900, 1020)), true));
        for (int i = 1; i < 7; i++) {
            dias.add(new DiaDeHuecos(lunes.plusDays(i), VentanaDelDia.diaEntero(), List.of(),
                    List.of(new TramoDelDia(0, 1440)), false));
        }
        return new SemanaDeHuecos(lunes, dias,
                List.of(new HuecoQueSeRepite(new TramoDelDia(900, 1020), List.of(1, 3, 4))), new LoReal(32, 6, 4));
    }

    /** Esa misma semana ya armada como la entregan las rutas. */
    public static HuecosDeLaSemana respuestaDelContrato(LocalDate lunes, LocalDate primeraSemana, boolean cerrada) {
        return ArmadoDeLosHuecos.armar(delContrato(lunes),
                new ArmadoDeLosHuecos.Lectura(primeraSemana, cerrada, lunes.plusDays(3)));
    }

    /**
     * La semana en curso ya CALCULADA (no de la foto): el lunes trae su agenda con nombres —Despertar 05:30, la reunion
     * de 09:00 a 10:30, una hora de agenda personal y Dormir 22:30— y lo cumplido (5 a tiempo, 2 tarde, 1 sin cumplir);
     * el martes no tiene Despertar. Los demas dias, en blanco.
     */
    public static HuecosDeLaSemana respuestaConAgenda(LocalDate lunes, LocalDate primeraSemana, LocalDate hoy) {
        List<DiaDeHuecos> dias = new ArrayList<>();
        dias.add(DiaDeHuecos.calcular(lunes,
                List.of(new HabitoConHora(HabitoConHora.DESPERTAR, LocalTime.of(5, 30), "Despertar"),
                        new HabitoConHora(HabitoConHora.DORMIR, LocalTime.of(22, 30), "Dormir")),
                List.of(ItemPlanificado.deEvento(lunes.atTime(9, 0), lunes.atTime(10, 30), "Reunion de equipo")),
                List.of(new TramoDelDia(840, 900))).conLoReal(new LoReal(5, 2, 1)));
        dias.add(DiaDeHuecos.calcular(lunes.plusDays(1),
                List.of(new HabitoConHora(HabitoConHora.DORMIR, LocalTime.of(22, 30), "Dormir")), List.of(), List.of())
                .conLoReal(LoReal.NADA));
        for (int i = 2; i < 7; i++) {
            dias.add(DiaDeHuecos.calcular(lunes.plusDays(i), List.of(), List.of(), List.of()).conLoReal(LoReal.NADA));
        }
        return ArmadoDeLosHuecos.armar(SemanaDeHuecos.de(lunes, dias, new LoReal(5, 2, 1)),
                new ArmadoDeLosHuecos.Lectura(primeraSemana, false, hoy));
    }
}
