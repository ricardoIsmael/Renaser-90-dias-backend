package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.domain.model.huecos.DiaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.HuecoQueSeRepite;
import com.renaser.os.mentoring.domain.model.huecos.LoReal;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.VentanaDelDia;
import com.renaser.os.shared.domain.TramoDelDia;

import java.time.LocalDate;
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
        return ArmadoDeLosHuecos.armar(delContrato(lunes), primeraSemana, cerrada);
    }
}
