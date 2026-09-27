package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocadiaria.AgregarRocaDiariaUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocadiaria.CupoDelDia;
import com.renaser.os.rocks.domain.model.rocadiaria.FechasPlanificables;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocasemanal.EstadoPlazo;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.SemanaPrograma;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Implementa {@link AgregarRocaDiariaUseCase} (D-177). Inserta una sola fila: las acciones que el dia
 * ya tenia no se leen para reescribirse, solo para saber que posicion queda libre.
 *
 * <p>Dos pedidos simultaneos para el mismo eje y dia calcularian la misma posicion; el segundo choca
 * con {@code UNIQUE (participante_id, fecha, eje, posicion)} de la base y no se guarda. Es un doble
 * toque, no un caso de negocio, y no se le agrega un bloqueo.
 */
@Service
class AgregarRocaDiariaService implements AgregarRocaDiariaUseCase {

    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final LoadRocaSemanalPort loadRocaSemanalPort;
    private final LoadRocaDiariaPort loadRocaDiariaPort;
    private final SaveRocaDiariaPort saveRocaDiariaPort;
    private final Clock clock;
    private final IdGenerator idGenerator;

    AgregarRocaDiariaService(ConsultarProgresoParticipanteRocksPort progresoPort,
                             LoadRocaMaestraPort loadRocaMaestraPort, LoadRocaSemanalPort loadRocaSemanalPort,
                             LoadRocaDiariaPort loadRocaDiariaPort, SaveRocaDiariaPort saveRocaDiariaPort, Clock clock,
                             IdGenerator idGenerator) {
        this.progresoPort = progresoPort;
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.loadRocaSemanalPort = loadRocaSemanalPort;
        this.loadRocaDiariaPort = loadRocaDiariaPort;
        this.saveRocaDiariaPort = saveRocaDiariaPort;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public RocaDiaria agregar(AgregarRocaDiariaCommand command) {
        ProgresoParticipanteRocks progreso = AccesoARocas.exigir(progresoPort, command.actorId());
        RocaMaestra maestra = maestraDelEje(command.actorId(), command.eje());
        LocalDate hoy = clock.now().atZone(progreso.zona()).toLocalDate();
        SemanaPrograma semanas = AccesoARocas.semanasParaPlanificarUnDia(progreso, hoy);
        requireDiaQueTodaviaNoLlego(command.fecha(), hoy, semanas);
        RocaSemanal semanal = semanalDeLaFecha(maestra, semanas, command.fecha());
        List<RocaDiaria> delDia = loadRocaDiariaPort.deParticipanteYFecha(command.actorId(), command.fecha());
        int posicion = CupoDelDia.siguientePosicion(delDia, command.eje());
        return saveRocaDiariaPort.save(RocaDiaria.planificar(RocaDiariaId.of(idGenerator.newId()),
                command.actorId(), command.fecha(), posicion, command.titulo(), null, command.puntajeImpacto(),
                command.esDelegable(), command.eje(), semanal.id(), command.horaInicio(), command.horaFin(),
                List.of(), clock));
    }

    /** Mismo requisito que {@code CrearPlanDiarioUseCase}: sin las tres Rocas Maestras no se planifica. */
    private RocaMaestra maestraDelEje(UserId actorId, EjeObjetivo eje) {
        List<RocaMaestra> maestras = loadRocaMaestraPort.deParticipante(actorId);
        if (maestras.size() < EjeObjetivo.values().length) {
            throw new NotAuthorizedException("ROCKS_LOCKED: completa tu onboarding antes de planificar rocas");
        }
        return maestras.stream().filter(maestra -> maestra.eje() == eje).findFirst()
                .orElseThrow(() -> new NotAuthorizedException("ROCKS_LOCKED: falta la Roca Maestra de " + eje));
    }

    /**
     * Hoy nunca (decision del dueno pendiente, ver el javadoc del caso de uso). De manana en adelante,
     * la ventana de {@code CrearPlanDiarioUseCase} con la noche ya abierta: hasta el domingo de esta
     * semana (en la 13, hasta el dia 90; D-203).
     *
     * <p><b>Corregido 2026-09-27 (D-203).</b> Decia "hasta el ultimo dia de esta semana de programa
     * (D-192)", cuando la semana era un bloque de siete dias del programa.
     */
    private static void requireDiaQueTodaviaNoLlego(LocalDate fecha, LocalDate hoy, SemanaPrograma semanas) {
        if (fecha.equals(hoy)) {
            throw new IllegalStateException("CURRENT_DAY: el dia en curso no se reacomoda");
        }
        FechasPlanificables fechas = FechasPlanificables.para(hoy, EstadoPlazo.EN_PLAZO, semanas);
        if (!fechas.contiene(fecha)) {
            throw new IllegalArgumentException(
                    "INVALID_DATE: la fecha debe estar entre " + fechas.desde() + " y " + fechas.hasta());
        }
    }

    private RocaSemanal semanalDeLaFecha(RocaMaestra maestra, SemanaPrograma semanas, LocalDate fecha) {
        int numeroSemana = semanas.numeroSemanaParaFecha(fecha);
        return loadRocaSemanalPort.deMaestraYSemana(maestra.id(), numeroSemana)
                .orElseThrow(() -> new IllegalArgumentException(
                        "NO_WEEKLY_ROCK: no hay plan semanal activo para el eje " + maestra.eje()));
    }
}
