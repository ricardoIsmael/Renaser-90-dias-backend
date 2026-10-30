package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort;
import com.renaser.os.habits.api.EvaluacionDelDiaPort.MarcaDeCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.cierre.SemanaDeCierres;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Los cierres de una semana de una persona, para las fichas (spec 017, FR-006): una sola regla para el mentor, la guía y el liderazgo.
 * Solo lee; no autoriza (quien llama ya lo hizo) y responde en la zona de la persona. Dos consultas por semana, no una por día.
 */
@Service
public class CierresDeLaSemanaService implements CierresDelDiaFinder {

    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final LoadRocaDiariaPort cargar;
    private final EvaluacionDelDiaPort marcas;
    private final Clock clock;

    CierresDeLaSemanaService(ConsultarProgresoParticipanteRocksPort progresoPort, LoadRocaDiariaPort cargar,
                             EvaluacionDelDiaPort marcas, Clock clock) {
        this.progresoPort = progresoPort;
        this.cargar = cargar;
        this.marcas = marcas;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public CierresDeLaSemana semanaDe(UserId personaId, LocalDate lunes) {
        ProgresoParticipanteRocks progreso = requireActiva(personaId);
        LocalDate hoy = clock.now().atZone(progreso.zona()).toLocalDate();
        LocalDate primerDia = progreso.primerDiaEfectivo(hoy).orElse(null);
        LocalDate primeraSemana = primerDia == null ? null : SemanaDeCierres.lunesDe(primerDia);
        LocalDate semana = SemanaDeCierres.resolver(lunes, hoy, primeraSemana);
        List<LocalDate> fechas = SemanaDeCierres.dias(semana, hoy, primerDia);
        List<DiaDelCierre> dias = fechas.isEmpty() ? List.of() : armar(personaId, fechas, hoy);
        return new CierresDeLaSemana(semana, primeraSemana, dias, ArmadoDelCierre.resumen(dias));
    }

    private List<DiaDelCierre> armar(UserId personaId, List<LocalDate> fechas, LocalDate hoy) {
        LocalDate desde = fechas.get(0);
        LocalDate hasta = fechas.get(fechas.size() - 1);
        Map<LocalDate, List<RocaDiaria>> rocas = cargar.deParticipanteEntreFechas(personaId, desde, hasta).stream()
                .collect(Collectors.groupingBy(RocaDiaria::fecha));
        Map<LocalDate, MarcaDeCierre> marcasPorDia = marcas.marcasEntre(personaId, desde, hasta).stream()
                .collect(Collectors.toMap(MarcaDeCierre::fecha, Function.identity(), (primera, otra) -> primera));
        return fechas.stream()
                .map(fecha -> ArmadoDelCierre.dia(fecha, hoy, rocas.getOrDefault(fecha, List.of()), marcasPorDia.get(fecha)))
                .toList();
    }

    private ProgresoParticipanteRocks requireActiva(UserId personaId) {
        ProgresoParticipanteRocks progreso = progresoPort.deParticipante(personaId)
                .orElseThrow(() -> new NoSuchElementException("Participante no encontrado: " + personaId));
        if (progreso.suspendido()) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return progreso;
    }
}
