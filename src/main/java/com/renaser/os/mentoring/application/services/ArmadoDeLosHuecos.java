package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.Dia;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.Hueco;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.HuecoRepetido;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.Resumen;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.Ventana;
import com.renaser.os.mentoring.domain.model.huecos.DiaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.HuecoQueSeRepite;
import com.renaser.os.mentoring.domain.model.huecos.LoReal;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.shared.domain.TramoDelDia;

import java.time.LocalDate;

/**
 * Del dominio a la respuesta de las rutas de huecos (contrato de la spec 015): las horas en {@code "HH:mm"}, el día de
 * la ventana en {@code null} cuando es el día entero. Es el ÚNICO lugar donde esa forma se arma, así que la semana en
 * curso, la foto guardada y las dos rutas dicen exactamente lo mismo (FR-025).
 */
final class ArmadoDeLosHuecos {

    private ArmadoDeLosHuecos() {
    }

    static HuecosDeLaSemana armar(SemanaDeHuecos semana, LocalDate primeraSemana, boolean cerrada) {
        return new HuecosDeLaSemana(semana.lunes(), primeraSemana, cerrada,
                semana.dias().stream().map(ArmadoDeLosHuecos::dia).toList(), resumen(semana));
    }

    private static Dia dia(DiaDeHuecos dia) {
        Ventana ventana = dia.ventana().vieneDeLaPersona()
                ? new Ventana(TramoDelDia.hora(dia.ventana().desde()), TramoDelDia.hora(dia.ventana().hasta())) : null;
        return new Dia(dia.fecha(), ventana, dia.minutosOcupados(), dia.minutosLibres(),
                dia.huecos().stream().map(ArmadoDeLosHuecos::hueco).toList());
    }

    private static Hueco hueco(TramoDelDia tramo) {
        return new Hueco(TramoDelDia.hora(tramo.desde()), TramoDelDia.hora(tramo.hasta()), tramo.minutos());
    }

    private static Resumen resumen(SemanaDeHuecos semana) {
        LoReal real = semana.real();
        return new Resumen(semana.minutosOcupados(), semana.minutosLibres(),
                semana.repetidos().stream().map(ArmadoDeLosHuecos::repetido).toList(),
                real.aTiempo(), real.tarde(), real.noCumplidos());
    }

    private static HuecoRepetido repetido(HuecoQueSeRepite repetido) {
        return new HuecoRepetido(TramoDelDia.hora(repetido.tramo().desde()),
                TramoDelDia.hora(repetido.tramo().hasta()), repetido.dias());
    }
}
