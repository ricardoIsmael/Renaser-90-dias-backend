package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaSinRedUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.CerrarElDia;
import com.renaser.os.rocks.application.ports.in.cierre.CerrarElDiaUseCase.RespuestaDeAccion;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase;
import com.renaser.os.rocks.application.ports.in.cierre.ConsultarCierreDelDiaUseCase.VistaDelCierre;
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
import com.renaser.os.shared.application.EntregaSinRed;
import com.renaser.os.shared.application.EntregaSinRed.Aplicado;
import com.renaser.os.shared.application.EntregaSinRed.Envio;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.RevisionDeLaHora;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * «Cierra tu día» hecho sin red (spec 048, fase 3, FR-019): las mismas reglas de {@link CierreDelDiaService}, corridas con la
 * hora real ({@link RevisionDeLaHora#referencia()}) en vez de la de llegada, y la regla de choques D13.
 *
 * <ul>
 *   <li><b>El día</b> es el de la hora real: cerrar el de ayer a las 21:40 y llegar hoy SÍ se aplica; cerrar un día que no es ese
 *       es {@code FUERA_DE_SU_DIA}.</li>
 *   <li><b>Qué se exige</b>: las pendientes cuya hora ya había llegado a la hora real y que existían ya a esa hora (las creadas
 *       después de {@code hechoEn} no las conocía el teléfono: no se exigen y no se tocan).</li>
 *   <li><b>Choque</b>: la nota del día o algún cierre de ese día cambió en el servidor después de {@code hechoEn}, o hay una pendiente
 *       exigida que el teléfono no conocía: {@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}. Una acción que ya no existe:
 *       {@code RECURSO_YA_NO_EXISTE}, todo o nada (P7).</li>
 *   <li>Cerrar sigue sin completar acciones, pagar puntos ni publicar eventos.</li>
 * </ul>
 *
 * <p>Nada de lo que escribió la persona (la nota, las respuestas) se loguea.
 */
@Service
class CierreDelDiaSinRedService implements CerrarElDiaSinRedUseCase {

    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final LoadRocaDiariaPort cargar;
    private final SaveRocaDiariaPort guardar;
    private final EvaluacionDelDiaPort marcas;
    private final ConsultarCierreDelDiaUseCase consulta;
    private final EntregaSinRed entrega;
    private final Clock clock;

    CierreDelDiaSinRedService(ConsultarProgresoParticipanteRocksPort progresoPort, LoadRocaDiariaPort cargar,
                              SaveRocaDiariaPort guardar, EvaluacionDelDiaPort marcas, ConsultarCierreDelDiaUseCase consulta,
                              EnviosSinRedPort envios, Clock clock) {
        this.progresoPort = progresoPort;
        this.cargar = cargar;
        this.guardar = guardar;
        this.marcas = marcas;
        this.consulta = consulta;
        this.entrega = new EntregaSinRed(envios, clock);
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public VistaDelCierre cerrar(CerrarElDia pedido, HechoSinRed sinRed) {
        ProgresoParticipanteRocks progreso = AccesoARocas.exigir(progresoPort, pedido.actorId());
        Envio envio = new Envio(pedido.actorId(), sinRed, "CIERRE_DEL_DIA", "cierre:" + pedido.fecha());
        return entrega.entregar(envio, () -> Aplicado.de(aplicar(pedido, sinRed, progreso)),
                previo -> consulta.consultar(pedido.actorId(), pedido.fecha()));
    }

    private VistaDelCierre aplicar(CerrarElDia pedido, HechoSinRed sinRed, ProgresoParticipanteRocks progreso) {
        RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
        LocalDate dia = hora.hoyEn(progreso.zona());
        if (!pedido.fecha().equals(dia)) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.FUERA_DE_SU_DIA);
        }
        String nota = NotaDeCierre.limpiar(pedido.nota());
        List<RocaDiaria> delDia = cargar.deParticipanteYFecha(pedido.actorId(), dia);
        exigirQueNadaHayaCambiado(pedido, hora, delDia);
        List<RocaDiaria> conocidas = delDia.stream().filter(roca -> !hora.hayCambioMasNuevo(roca.creadoEn())).toList();
        MomentoDelCierre momento = MomentoDelCierre.de(hora.referencia(), progreso.zona());
        exigirUnaRespuestaPorPendiente(conocidas, pedido, momento);
        guardar.guardarSoloElCierre(CierreDelDia.cerrar(conocidas, respuestasDe(pedido), momento));
        marcas.dejarCerrado(pedido.actorId(), dia, nota, hora.referencia());
        return consulta.consultar(pedido.actorId(), dia);
    }

    /** D13: lo del servidor posterior a lo que hiciste gana; una acción que ya no está lo deja todo sin aplicar (P7). */
    private void exigirQueNadaHayaCambiado(CerrarElDia pedido, RevisionDeLaHora hora, List<RocaDiaria> delDia) {
        Set<RocaDiariaId> existentes = new HashSet<>();
        delDia.forEach(roca -> existentes.add(roca.id()));
        if (pedido.acciones().stream().anyMatch(r -> !existentes.contains(RocaDiariaId.of(r.accionId())))) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE);
        }
        boolean notaMasNueva = marcas.actualizadoEn(pedido.actorId(), pedido.fecha()).filter(hora::hayCambioMasNuevo).isPresent();
        boolean cierreMasNuevo = delDia.stream().anyMatch(roca -> roca.cierre() != null && hora.hayCambioMasNuevo(roca.cierre().cerradoEn()));
        if (notaMasNueva || cierreMasNuevo) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
    }

    /** Una pendiente que ya existía y se pregunta, sin respuesta: el teléfono no la conocía (se creó desde otro lado). */
    private static void exigirUnaRespuestaPorPendiente(List<RocaDiaria> conocidas, CerrarElDia pedido, MomentoDelCierre momento) {
        Set<RocaDiariaId> respondidas = new HashSet<>();
        pedido.acciones().forEach(r -> respondidas.add(RocaDiariaId.of(r.accionId())));
        boolean faltaUna = conocidas.stream().anyMatch(roca -> CierreDelDia.seExigeRespuesta(roca, momento) && !respondidas.contains(roca.id()));
        if (faltaUna) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
    }

    private static List<RespuestaDeUnaAccion> respuestasDe(CerrarElDia pedido) {
        return pedido.acciones().stream().map(CierreDelDiaSinRedService::respuestaDe).toList();
    }

    private static RespuestaDeUnaAccion respuestaDe(RespuestaDeAccion r) {
        return new RespuestaDeUnaAccion(RocaDiariaId.of(r.accionId()),
                new RespuestaDeCierre(MotivoDeAccionPendiente.desde(r.motivo()), PlanDeManana.desde(r.manana()), r.nota()));
    }
}
