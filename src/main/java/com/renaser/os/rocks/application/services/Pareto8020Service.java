package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort;
import com.renaser.os.rocks.api.Pareto8020Finder;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * {@link Pareto8020Finder}: lee las acciones del día y las marcas de «Cierra tu día» de la persona en un rango y deja la cuenta a
 * {@link ArmadoDel8020}. Dos consultas, sin importar cuántos días tenga el periodo. No autoriza ni mira la cuenta: quien llama ya lo
 * hizo. El «hoy» es el de la zona de la persona; si todavía no tiene programa, el del servidor en Lima (el padrón entero vive allí).
 */
@Service
class Pareto8020Service implements Pareto8020Finder {

    private static final ZoneId ZONA_DEL_PADRON = ZoneId.of("America/Lima");

    private final LoadRocaDiariaPort cargar;
    private final EvaluacionDelDiaPort marcas;
    private final ConsultarProgresoParticipanteRocksPort progreso;
    private final Clock clock;

    Pareto8020Service(LoadRocaDiariaPort cargar, EvaluacionDelDiaPort marcas,
                      ConsultarProgresoParticipanteRocksPort progreso, Clock clock) {
        this.cargar = cargar;
        this.marcas = marcas;
        this.progreso = progreso;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public InsumosDel8020 insumos(UserId personaId, LocalDate desde, LocalDate hasta) {
        if (hasta.isBefore(desde)) {
            throw new IllegalArgumentException("El periodo termina antes de empezar");
        }
        LocalDate hoy = clock.now().atZone(zonaDe(personaId)).toLocalDate();
        return ArmadoDel8020.armar(cargar.deParticipanteEntreFechas(personaId, desde, hasta),
                marcas.marcasEntre(personaId, desde, hasta), desde, hasta, hoy);
    }

    private ZoneId zonaDe(UserId personaId) {
        return progreso.deParticipante(personaId)
                .map(ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks::zona)
                .orElse(ZONA_DEL_PADRON);
    }
}
