package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.registro.ContarCumplimientoPorHabitoPort;
import com.renaser.os.habits.domain.model.registro.CumplimientoDeUnHabito;
import com.renaser.os.points.api.CumplimientoDeHabito;
import com.renaser.os.points.api.CumplimientoPorHabitoFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * D-277 — implementa {@link CumplimientoPorHabitoFinder} para el mensaje del semaforo con una sola consulta por
 * persona ({@link ContarCumplimientoPorHabitoPort}). Lo que reporta como {@code programados} es
 * {@link CumplimientoDeUnHabito#calificables()}: la misma regla de "este habito contaba ese dia" que ya aplican el
 * semaforo y el ranking ({@code ConteoDiarioHabitos}), sin copiarla.
 */
@Service
public class CumplimientoPorHabitoFinderService implements CumplimientoPorHabitoFinder {

    private final ContarCumplimientoPorHabitoPort contarPort;

    public CumplimientoPorHabitoFinderService(ContarCumplimientoPorHabitoPort contarPort) {
        this.contarPort = contarPort;
    }

    @Override
    public List<CumplimientoDeHabito> enRango(UserId participante, LocalDate desde, LocalDate hasta) {
        return contarPort.deParticipanteEntre(participante, desde, hasta).stream()
                .map(habito -> new CumplimientoDeHabito(habito.nombre(), habito.calificables(), habito.completados()))
                .toList();
    }
}
