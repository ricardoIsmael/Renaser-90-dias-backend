package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.DiaDeLaFicha;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.application.services.LecturaDeLaFicha.Lectura;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.domain.model.cumplimiento.ColorDeLaFila;
import com.renaser.os.leadership.domain.model.cumplimiento.MotivoDelDia;
import com.renaser.os.leadership.domain.model.cumplimiento.VentanaDeLaFicha;
import com.renaser.os.mentoring.api.DiaDelAlumno;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.rocks.api.AccionesDelDiaFinder.AccionDelDia;
import com.renaser.os.users.api.UserSummary;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Junta lo que se leyó en la ficha de una persona (spec 005): el color y el porcentaje de la ventana, y por día sus
 * obligaciones, sus acciones y —si el día no tiene nada que medir— el motivo en palabras.
 *
 * <p>Es una función pura: no lee nada ni decide nada que no esté en la {@link Lectura}.
 */
final class ArmadoDeLaFicha {

    private ArmadoDeLaFicha() {
    }

    static SemanaDeLaPersona armar(Miembro miembro, VentanaDeLaFicha ventana, Lectura lectura) {
        UserSummary perfil = miembro.perfil();
        VentanaDelSemaforo suya = lectura.semaforo().orElse(null);
        return new SemanaDeLaPersona(lectura.semana().grupoId(), perfil.id().value(), perfil.fullName(),
                perfil.avatarUrl(), perfil.role(), ventana.desde(), ventana.hasta(), miembro.participacion().zona().getId(),
                ventana.cerrada(), primeraSemana(miembro), lectura.mentor(), color(miembro, suya),
                suya == null ? null : suya.porcentaje(), lectura.semana().resumen(), dias(lectura, ventana, suya));
    }

    /** El sábado que abre la semana del Día 1; no hay semanas antes. Null mientras no eligió su Día 1. */
    private static LocalDate primeraSemana(Miembro miembro) {
        LocalDate diaUno = miembro.participacion().diaUnoElegido();
        return diaUno == null ? null : VentanaDeLaFicha.sabadoQueAbreLaSemanaDe(diaUno);
    }

    /**
     * Quien lleva su programa en el grupo no tiene color (FR-006). Quien el semáforo no mide, tampoco: «sin datos».
     * Un porcentaje no se convierte nunca en cero ni en verde por falta de datos.
     */
    private static ColorDeLaFila color(Miembro miembro, VentanaDelSemaforo suya) {
        if (miembro.acompanante()) {
            return ColorDeLaFila.NO_SE_MIDE;
        }
        return suya == null ? ColorDeLaFila.SIN_DATOS : ColorDeLaFila.de(suya.color());
    }

    private static List<DiaDeLaFicha> dias(Lectura lectura, VentanaDeLaFicha ventana, VentanaDelSemaforo suya) {
        Map<LocalDate, DiaDelSemaforo> delSemaforo = new HashMap<>();
        if (suya != null) {
            suya.dias().forEach(dia -> delSemaforo.put(dia.fecha(), dia));
        }
        return lectura.semana().dias().stream()
                .map(dia -> dia(dia, ventana, delSemaforo.get(dia.fecha()), lectura.acciones(), suya == null))
                .toList();
    }

    private static DiaDeLaFicha dia(DiaDelAlumno dia, VentanaDeLaFicha ventana, DiaDelSemaforo delSemaforo,
                                    List<AccionDelDia> acciones, boolean sinSemaforo) {
        if (ventana.esFutura(dia.fecha())) {
            // Un dia que no llego se muestra para que la semana se vea entera, pero no tiene nada: ni las acciones
            // que la persona ya planifico para manana.
            return new DiaDeLaFicha(dia.fecha(), null, MotivoDelDia.FUTURO.name(), List.of(), List.of());
        }
        List<AccionDelDia> delDia = acciones.stream().filter(accion -> accion.fecha().equals(dia.fecha())).toList();
        return new DiaDeLaFicha(dia.fecha(), dia.diaDePrograma(), motivo(dia.fecha(), ventana, delSemaforo, sinSemaforo),
                dia.obligaciones(), delDia);
    }

    /** Por que el dia no tiene nada que medir, en palabras; null si se midio. Nunca un incumplimiento. */
    private static String motivo(LocalDate fecha, VentanaDeLaFicha ventana, DiaDelSemaforo delSemaforo,
                                 boolean sinSemaforo) {
        if (sinSemaforo) {
            return MotivoDelDia.NO_ACTIVADO.name();
        }
        Optional<MotivoDelDia> delEstado = delSemaforo == null ? Optional.empty() : MotivoDelDia.de(delSemaforo.estado());
        if (ventana.esHoy(fecha) && delEstado.isPresent()) {
            return MotivoDelDia.EN_CURSO.name();
        }
        return delEstado.map(Enum::name).orElse(null);
    }
}
