package com.renaser.os.mentoring.application.services;

import com.renaser.os.calendar.api.EventosDelParticipanteFinder;
import com.renaser.os.habits.api.HorarioDelDiaFinder;
import com.renaser.os.habits.api.RegistrosDeHabitoFinder;
import com.renaser.os.habits.api.RegistrosDeHabitoFinder.RegistroDeHabito;
import com.renaser.os.mentoring.domain.model.huecos.Cumplible;
import com.renaser.os.mentoring.domain.model.huecos.DiaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.ItemPlanificado;
import com.renaser.os.mentoring.domain.model.huecos.LoReal;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.rag.api.AgendaOcupadaFinder;
import com.renaser.os.rocks.api.HorasDeLasAccionesFinder;
import com.renaser.os.rocks.api.HorasDeLasAccionesFinder.HoraDeUnaAccion;
import com.renaser.os.shared.domain.TramoDelDia;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Calcula los huecos de una semana de una persona juntando lo que cada módulo ya sabe de ella (spec 015): sus hábitos
 * con el horario que rige cada fecha ({@code habits}), sus acciones del día con hora ({@code rocks}), sus eventos
 * ({@code calendar}) y las horas ocupadas que guardó con SER ({@code rag}). Las cuentas las hace el dominio; acá solo
 * se lee y se acomoda.
 *
 * <p><b>Solo lectura</b> (FR-012): no crea ni cambia horarios, no gasta cupo, no da puntos y no toca racha, semáforo ni
 * coherencia. Sin Día 1 elegido no hay hábitos del programa que contar (FR-011); lo demás se calcula igual.
 */
@Component
class CalculoDeLosHuecos {

    private static final int DIAS_DE_LA_SEMANA = 7;
    private static final String NO_VA = "NO_ASISTE";

    private final HorarioDelDiaFinder horarios;
    private final RegistrosDeHabitoFinder registros;
    private final HorasDeLasAccionesFinder acciones;
    private final EventosDelParticipanteFinder eventos;
    private final AgendaOcupadaFinder agenda;

    CalculoDeLosHuecos(HorarioDelDiaFinder horarios, RegistrosDeHabitoFinder registros,
                       HorasDeLasAccionesFinder acciones, EventosDelParticipanteFinder eventos,
                       AgendaOcupadaFinder agenda) {
        this.horarios = horarios;
        this.registros = registros;
        this.acciones = acciones;
        this.eventos = eventos;
        this.agenda = agenda;
    }

    /**
     * @param lunes el lunes de la semana
     * @param hoy   el día de hoy en la zona de la persona: lo que ya pasó se mide con registros, lo que viene con el plan
     */
    SemanaDeHuecos calcular(ParticipacionPrograma persona, LocalDate lunes, LocalDate hoy) {
        Fuentes fuentes = leer(persona, lunes);
        List<DiaDeHuecos> dias = new ArrayList<>();
        List<Cumplible> cumplibles = new ArrayList<>();
        for (int i = 0; i < DIAS_DE_LA_SEMANA; i++) {
            LocalDate fecha = lunes.plusDays(i);
            HabitosDeUnDia habitos = habitosDe(persona, fecha, hoy, fuentes);
            dias.add(DiaDeHuecos.calcular(fecha, habitos.ocupan(), fuentes.otrosDe(fecha), fuentes.agendaDe(fecha)));
            cumplibles.addAll(habitos.cumplibles());
            cumplibles.addAll(fuentes.cumplibles(fecha));
        }
        return SemanaDeHuecos.de(lunes, dias, LoReal.de(persona.zona(), hoy, cumplibles));
    }

    private Fuentes leer(ParticipacionPrograma persona, LocalDate lunes) {
        UserId id = persona.participanteId();
        LocalDate domingo = lunes.plusDays(DIAS_DE_LA_SEMANA - 1L);
        Map<LocalDate, List<RegistroDeHabito>> registrosPorDia = persona.activado()
                ? registros.deParticipanteEntre(id, lunes, domingo).stream()
                        .collect(Collectors.groupingBy(RegistroDeHabito::fecha))
                : Map.of();
        // Un dia antes: un evento de la noche anterior que cruza la medianoche deja su pedazo en el lunes.
        List<ItemPlanificado> delCalendario = eventos.entre(id, lunes.minusDays(1), domingo).eventos().stream()
                .filter(evento -> !NO_VA.equals(evento.asistencia()))
                .map(evento -> ItemPlanificado.deEvento(evento.iniciaLocal(), evento.terminaLocal()))
                .toList();
        return new Fuentes(registrosPorDia, acciones.entre(id, lunes, domingo), delCalendario, agenda.semanaDe(id));
    }

    private HabitosDeUnDia habitosDe(ParticipacionPrograma persona, LocalDate fecha, LocalDate hoy, Fuentes fuentes) {
        if (!persona.activado()) {
            return HabitosDeUnDia.ninguno();
        }
        return HabitosDeUnDia.de(hoy, horarios.deFecha(persona.participanteId(), fecha), fuentes.registrosDe(fecha));
    }

    /** Lo leído de los otros módulos para la semana entera, con lo que cada día saca de ahí. */
    private record Fuentes(Map<LocalDate, List<RegistroDeHabito>> registros, List<HoraDeUnaAccion> acciones,
                           List<ItemPlanificado> eventos, Map<DayOfWeek, List<TramoDelDia>> agenda) {

        List<RegistroDeHabito> registrosDe(LocalDate fecha) {
            return registros.getOrDefault(fecha, List.of());
        }

        /** Los eventos de toda la semana (cada uno se parte en los días que toca) y las acciones del día con hora. */
        List<ItemPlanificado> otrosDe(LocalDate fecha) {
            List<ItemPlanificado> otros = new ArrayList<>(eventos);
            accionesDe(fecha).stream().filter(accion -> accion.horaInicio() != null)
                    .forEach(accion -> otros.add(ItemPlanificado.deAccion(fecha, accion.horaInicio(), accion.horaFin())));
            return otros;
        }

        List<TramoDelDia> agendaDe(LocalDate fecha) {
            return agenda.getOrDefault(fecha.getDayOfWeek(), List.of());
        }

        List<Cumplible> cumplibles(LocalDate fecha) {
            return accionesDe(fecha).stream()
                    .map(accion -> new Cumplible(fecha, accion.horaInicio(), accion.horaFin(),
                            accion.completada() ? accion.completadaEn() : null, false))
                    .toList();
        }

        private List<HoraDeUnaAccion> accionesDe(LocalDate fecha) {
            return acciones.stream().filter(accion -> accion.fecha().equals(fecha)).toList();
        }
    }
}
