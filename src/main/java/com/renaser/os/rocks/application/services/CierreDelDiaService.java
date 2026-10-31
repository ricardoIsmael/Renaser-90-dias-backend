package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort;
import com.renaser.os.habits.api.EvaluacionDelDiaPort.MarcaDeCierre;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.application.ports.out.rocadiaria.SaveRocaDiariaPort;
import com.renaser.os.rocks.domain.model.cierre.MomentoDelCierre;
import com.renaser.os.rocks.domain.model.cierre.MotivoDeAccionPendiente;
import com.renaser.os.rocks.domain.model.cierre.NotaDeCierre;
import com.renaser.os.rocks.domain.model.cierre.PlanDeManana;
import com.renaser.os.rocks.domain.model.cierre.RespuestaDeCierre;
import com.renaser.os.rocks.domain.model.rocadiaria.CierreDelDia;
import com.renaser.os.rocks.domain.model.rocadiaria.CierreDelDia.RespuestaDeUnaAccion;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiariaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * «Cierra tu día» de la propia persona (spec 017): consultar el cierre de un día y cerrar el de hoy.
 *
 * <p><b>Cerrar nunca cumple una acción ni paga puntos.</b> Escribe solo las columnas de cierre
 * ({@link SaveRocaDiariaPort#guardarSoloElCierre}) y la marca del día ({@link EvaluacionDelDiaPort}); no usa
 * {@code CompletarRocaDiariaUseCase}, ni {@code AjustarPuntosPort}, ni publica eventos. Todo en una transacción: si falla un paso no
 * queda la marca sin las respuestas.
 *
 * <p>«Hoy» es el de la zona de la persona (regla 02) y es lo único que se exige del día. No hay hora de cierre: la fila de la app está
 * siempre, todos los días y a cualquier hora (corregido el 2026-10-30, pedido del dueño), y no depende de Despertar ni de Dormir.
 *
 * <p><b>Solo se exige respuesta de las pendientes cuya hora ya llegó</b> (decisión del dueño, 2026-10-30): la hora local sale del
 * {@code Clock} y la zona de la persona, nunca del teléfono ni de la zona del servidor. Las de hora futura no se preguntan; las que no
 * tienen hora se preguntan como siempre. La lectura de hoy dice cuáles se preguntan ({@code exigidas}) para que la app y el servidor no
 * puedan discrepar.
 */
@Service
public class CierreDelDiaService implements CerrarElDiaUseCase, ConsultarCierreDelDiaUseCase {

    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final LoadRocaDiariaPort cargar;
    private final SaveRocaDiariaPort guardar;
    private final EvaluacionDelDiaPort marcas;
    private final Clock clock;

    CierreDelDiaService(ConsultarProgresoParticipanteRocksPort progresoPort, LoadRocaDiariaPort cargar,
                        SaveRocaDiariaPort guardar, EvaluacionDelDiaPort marcas, Clock clock) {
        this.progresoPort = progresoPort;
        this.cargar = cargar;
        this.guardar = guardar;
        this.marcas = marcas;
        this.clock = clock;
    }

    @Override
    @Transactional
    public VistaDelCierre cerrar(CerrarElDia pedido) {
        ProgresoParticipanteRocks progreso = AccesoARocas.exigir(progresoPort, pedido.actorId());
        LocalDate hoy = hoyDe(progreso);
        CierreDelDia.exigirQueSeaHoy(pedido.fecha(), hoy);
        String notaDelDia = NotaDeCierre.limpiar(pedido.nota());
        List<RocaDiaria> delDia = cargar.deParticipanteYFecha(pedido.actorId(), hoy);
        MomentoDelCierre momento = MomentoDelCierre.de(clock.now(), progreso.zona());
        List<RocaDiaria> cambiadas = CierreDelDia.cerrar(delDia, respuestasDe(pedido), momento);
        guardar.guardarSoloElCierre(cambiadas);
        marcas.dejarCerrado(pedido.actorId(), hoy, notaDelDia);
        return vistaDe(progreso, pedido.actorId(), hoy, hoy);
    }

    @Override
    @Transactional(readOnly = true)
    public VistaDelCierre consultar(UserId actorId, LocalDate fecha) {
        ProgresoParticipanteRocks progreso = AccesoARocas.exigir(progresoPort, actorId);
        LocalDate hoy = hoyDe(progreso);
        LocalDate dia = fecha == null ? hoy : fecha;
        if (dia.isAfter(hoy)) {
            throw new IllegalArgumentException("INVALID_DATE: ese día todavía no llega");
        }
        return vistaDe(progreso, actorId, dia, hoy);
    }

    private VistaDelCierre vistaDe(ProgresoParticipanteRocks progreso, UserId actorId, LocalDate dia, LocalDate hoy) {
        List<RocaDiaria> rocas = cargar.deParticipanteYFecha(actorId, dia);
        MarcaDeCierre marca = marcas.marcasEntre(actorId, dia, dia).stream().findFirst().orElse(null);
        boolean esHoy = dia.equals(hoy);
        return new VistaDelCierre(ArmadoDelCierre.dia(dia, hoy, rocas, marca), esHoy,
                esHoy ? avanceDeLaSemana(progreso, actorId, hoy) : null, esHoy ? exigidas(rocas, progreso) : Set.of());
    }

    /** Las acciones por las que hoy se pregunta: pendientes cuya hora ya llegó, o sin hora. Un día anterior no pregunta nada. */
    private Set<UUID> exigidas(List<RocaDiaria> rocas, ProgresoParticipanteRocks progreso) {
        MomentoDelCierre momento = MomentoDelCierre.de(clock.now(), progreso.zona());
        return rocas.stream().filter(roca -> CierreDelDia.seExigeRespuesta(roca, momento))
                .map(roca -> roca.id().value()).collect(Collectors.toSet());
    }

    /** Las acciones de la semana de programa hasta hoy (D-203): la misma cuenta que {@code progresoSemanalPct} del tablero. */
    private AvanceDeLaSemana avanceDeLaSemana(ProgresoParticipanteRocks progreso, UserId actorId, LocalDate hoy) {
        return progreso.semanas(hoy)
                .map(semanas -> semanas.limites(semanas.numeroSemanaParaFecha(hoy)).inicio())
                .map(inicio -> ArmadoDelCierre.avance(cargar.deParticipanteEntreFechas(actorId, inicio, hoy)))
                .orElse(null);
    }

    private static List<RespuestaDeUnaAccion> respuestasDe(CerrarElDia pedido) {
        return pedido.acciones().stream()
                .map(r -> new RespuestaDeUnaAccion(RocaDiariaId.of(r.accionId()),
                        new RespuestaDeCierre(MotivoDeAccionPendiente.desde(r.motivo()), PlanDeManana.desde(r.manana()), r.nota())))
                .toList();
    }

    private LocalDate hoyDe(ProgresoParticipanteRocks progreso) {
        return clock.now().atZone(progreso.zona()).toLocalDate();
    }
}
