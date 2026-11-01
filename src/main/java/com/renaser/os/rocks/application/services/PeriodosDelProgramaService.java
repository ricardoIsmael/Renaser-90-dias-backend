package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.api.PeriodosDelProgramaFinder;
import com.renaser.os.rocks.domain.model.analisis.CalendarioDelAnalisis;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * {@link PeriodosDelProgramaFinder} sobre {@link CalendarioDelAnalisis}: solo traduce entre los tipos del dominio y los del contrato.
 * Sin estado, sin base de datos y sin reloj: todo llega por parámetro.
 */
@Service
class PeriodosDelProgramaService implements PeriodosDelProgramaFinder {

    @Override
    public PeriodoDelPrograma semanaDe(LocalDate primerDia, int duracionDias, LocalDate fecha) {
        return aContrato(CalendarioDelAnalisis.desde(primerDia, duracionDias).semanaDe(fecha));
    }

    @Override
    public PeriodoDelPrograma mesDe(LocalDate primerDia, int duracionDias, LocalDate fecha) {
        return aContrato(CalendarioDelAnalisis.desde(primerDia, duracionDias).mesDe(fecha));
    }

    @Override
    public Optional<PeriodoDelPrograma> queEmpiezaEn(LocalDate primerDia, int duracionDias, Tipo tipo, LocalDate inicio) {
        CalendarioDelAnalisis calendario = CalendarioDelAnalisis.desde(primerDia, duracionDias);
        if (inicio.isBefore(primerDia)) {
            return Optional.empty();
        }
        CalendarioDelAnalisis.Periodo candidato = tipo == Tipo.SEMANA ? calendario.semanaDe(inicio) : calendario.mesDe(inicio);
        return Optional.of(candidato).filter(periodo -> periodo.inicio().equals(inicio)).map(PeriodosDelProgramaService::aContrato);
    }

    @Override
    public Optional<PeriodoDelPrograma> anteriorA(LocalDate primerDia, int duracionDias, PeriodoDelPrograma periodo) {
        if (periodo.numero() <= 1) {
            return Optional.empty();
        }
        CalendarioDelAnalisis calendario = CalendarioDelAnalisis.desde(primerDia, duracionDias);
        int numero = periodo.numero() - 1;
        return Optional.of(aContrato(periodo.tipo() == Tipo.SEMANA ? calendario.semana(numero) : calendario.mes(numero)));
    }

    @Override
    public Optional<PeriodoDelPrograma> siguienteA(LocalDate primerDia, int duracionDias, PeriodoDelPrograma periodo, LocalDate hoy) {
        CalendarioDelAnalisis calendario = CalendarioDelAnalisis.desde(primerDia, duracionDias);
        int numero = periodo.numero() + 1;
        CalendarioDelAnalisis.Periodo siguiente = periodo.tipo() == Tipo.SEMANA ? calendario.semana(numero) : calendario.mes(numero);
        return Optional.of(siguiente).filter(candidato -> !candidato.inicio().isAfter(hoy)).map(PeriodosDelProgramaService::aContrato);
    }

    @Override
    public List<PeriodoDelPrograma> semanasDelMes(LocalDate primerDia, int duracionDias, PeriodoDelPrograma mes) {
        return CalendarioDelAnalisis.desde(primerDia, duracionDias).semanasDelMes(mes.numero()).stream()
                .map(PeriodosDelProgramaService::aContrato).toList();
    }

    @Override
    public List<PeriodoDelPrograma> cerradosHastaHoy(LocalDate primerDia, int duracionDias, LocalDate hoy, int diasDeVentana) {
        return CalendarioDelAnalisis.desde(primerDia, duracionDias).cerrados(hoy, diasDeVentana).stream()
                .map(PeriodosDelProgramaService::aContrato).toList();
    }

    private static PeriodoDelPrograma aContrato(CalendarioDelAnalisis.Periodo periodo) {
        return new PeriodoDelPrograma(Tipo.valueOf(periodo.tipo().name()), periodo.numero(), periodo.inicio(), periodo.fin());
    }
}
