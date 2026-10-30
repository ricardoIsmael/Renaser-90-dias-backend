package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.Dia;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.Hueco;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.HuecoRepetido;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.Ocupado;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.Resumen;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.SinVentanaPor;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.Tipo;
import com.renaser.os.mentoring.api.HuecosDeLaSemana.Ventana;
import com.renaser.os.mentoring.domain.model.huecos.BloqueOcupado;
import com.renaser.os.mentoring.domain.model.huecos.DetalleDelDia;
import com.renaser.os.mentoring.domain.model.huecos.DiaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.HuecoQueSeRepite;
import com.renaser.os.mentoring.domain.model.huecos.LoReal;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.shared.domain.TramoDelDia;

import java.time.LocalDate;
import java.util.List;

/**
 * Del dominio a la respuesta de las rutas de huecos (contrato de la spec 015): las horas en {@code "HH:mm"}, el día de
 * la ventana en {@code null} cuando es el día entero. Es el ÚNICO lugar donde esa forma se arma, así que la semana en
 * curso, la foto guardada y las dos rutas dicen exactamente lo mismo (FR-025).
 *
 * <p>Un día que sale de la foto no tiene {@link DiaDeHuecos#detalle()}: sus bloques ocupados, lo cumplido y el motivo de
 * un día sin ventana salen en {@code null}, y quien los lee lo tolera.
 */
final class ArmadoDeLosHuecos {

    private ArmadoDeLosHuecos() {
    }

    /**
     * @param primeraSemana el lunes de su Día 1; {@code null} mientras no lo eligió
     * @param cerrada       {@code true} si la semana sale de la foto guardada
     * @param hoy           el día de hoy en la zona de la persona
     */
    record Lectura(LocalDate primeraSemana, boolean cerrada, LocalDate hoy) {
    }

    static HuecosDeLaSemana armar(SemanaDeHuecos semana, Lectura lectura) {
        return new HuecosDeLaSemana(semana.lunes(), lectura.primeraSemana(), lectura.hoy(), lectura.cerrada(),
                semana.dias().stream().map(ArmadoDeLosHuecos::dia).toList(), resumen(semana));
    }

    private static Dia dia(DiaDeHuecos dia) {
        Ventana ventana = dia.ventana().vieneDeLaPersona()
                ? new Ventana(TramoDelDia.hora(dia.ventana().desde()), TramoDelDia.hora(dia.ventana().hasta())) : null;
        List<Hueco> huecos = dia.huecos().stream().map(ArmadoDeLosHuecos::hueco).toList();
        DetalleDelDia detalle = dia.detalle();
        if (detalle == null) {
            return new Dia(dia.fecha(), ventana, null, dia.minutosOcupados(), dia.minutosLibres(), huecos, null, null,
                    null, null);
        }
        return new Dia(dia.fecha(), ventana, motivo(detalle), dia.minutosOcupados(), dia.minutosLibres(), huecos,
                detalle.bloques().stream().map(ArmadoDeLosHuecos::ocupado).toList(), detalle.real().aTiempo(),
                detalle.real().tarde(), detalle.real().noCumplidos());
    }

    private static SinVentanaPor motivo(DetalleDelDia detalle) {
        return detalle.sinVentana() == null ? null : SinVentanaPor.valueOf(detalle.sinVentana().name());
    }

    private static Ocupado ocupado(BloqueOcupado bloque) {
        return new Ocupado(TramoDelDia.hora(bloque.tramo().desde()), TramoDelDia.hora(bloque.tramo().hasta()),
                Tipo.valueOf(bloque.tipo().name()), bloque.titulo());
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
