package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase.DefinirRocaMaestraCommand;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase;
import com.renaser.os.rocks.application.ports.in.rocamensual.DefinirRocaMensualUseCase.DefinirRocaMensualCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CerrarSemanaUseCase.CerrarSemanaCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.CrearPlanSemanalUseCase.CrearPlanSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase;
import com.renaser.os.rocks.application.ports.in.rocasemanal.EditarDentroDe48hUseCase.EditarRocaSemanalCommand;
import com.renaser.os.rocks.application.ports.in.rocasemanal.ObjetivosSinRedUseCase;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamensual.LoadRocaMensualPort;
import com.renaser.os.rocks.application.ports.out.rocasemanal.LoadRocaSemanalPort;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamaestra.RocasMaestras;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.application.EntregaSinRed;
import com.renaser.os.shared.application.EntregaSinRed.Aplicado;
import com.renaser.os.shared.application.EntregaSinRed.Envio;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RevisionDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Los objetivos del Plan hechos sin red (spec 048, fase 3, FR-019): el plan semanal y su revisión, el objetivo de 90 días y el del mes.
 * Las reglas de los servicios de siempre corridas con la hora real ({@link RevisionDeLaHora#referencia()}) y la regla de choques de D13:
 * <b>gana lo más nuevo</b>. Cada fila ya tiene su {@code actualizado_en} (R1), que es la hora lógica contra la que se compara
 * {@code hechoEn}.
 *
 * <ul>
 *   <li><b>Semana nueva</b>: si todos los ejes pedidos ya tenían objetivo, no se aplica; si alguno se creó después de lo que hiciste es
 *       {@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}, si no {@code YA_ESTABA_HECHO}. Los ejes que faltan se crean y los que ya estaban se
 *       respetan, como con red.</li>
 *   <li><b>Editar o revisar la semana</b>, <b>objetivo de 90 días</b> y <b>del mes</b>: si la fila cambió después de lo que hiciste, no
 *       se aplica; si ya no existe, {@code RECURSO_YA_NO_EXISTE}. Un objetivo de 90 días por confirmar (nacido del diagnóstico) no
 *       cuenta como cambio: su hora es la de la materialización, no la de una edición.</li>
 * </ul>
 */
@Service
class ObjetivosSinRedService implements ObjetivosSinRedUseCase {

    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final LoadRocaMaestraPort loadRocaMaestraPort;
    private final LoadRocaSemanalPort loadRocaSemanalPort;
    private final LoadRocaMensualPort loadRocaMensualPort;
    private final CrearPlanSemanalUseCase crearSemana;
    private final EditarDentroDe48hUseCase editarSemanal;
    private final CerrarSemanaUseCase cerrarSemana;
    private final DefinirRocaMaestraUseCase definirMaestra;
    private final DefinirRocaMensualUseCase definirMensual;
    private final EntregaSinRed entrega;
    private final Clock clock;

    ObjetivosSinRedService(ConsultarProgresoParticipanteRocksPort progresoPort, LoadRocaMaestraPort loadRocaMaestraPort,
                           LoadRocaSemanalPort loadRocaSemanalPort, LoadRocaMensualPort loadRocaMensualPort,
                           CrearPlanSemanalUseCase crearSemana, EditarDentroDe48hUseCase editarSemanal,
                           CerrarSemanaUseCase cerrarSemana, DefinirRocaMaestraUseCase definirMaestra,
                           DefinirRocaMensualUseCase definirMensual, EnviosSinRedPort envios, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadRocaMaestraPort = loadRocaMaestraPort;
        this.loadRocaSemanalPort = loadRocaSemanalPort;
        this.loadRocaMensualPort = loadRocaMensualPort;
        this.crearSemana = crearSemana;
        this.editarSemanal = editarSemanal;
        this.cerrarSemana = cerrarSemana;
        this.definirMaestra = definirMaestra;
        this.definirMensual = definirMensual;
        this.entrega = new EntregaSinRed(envios, clock);
        this.clock = clock;
    }

    // ─── semana ────────────────────────────────────────────────────────────

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public List<RocaSemanal> crearSemana(CrearPlanSemanalCommand command, HechoSinRed sinRed) {
        ProgresoParticipanteRocks progreso = AccesoARocas.exigir(progresoPort, command.actorId());
        RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
        int semana = RocaSemanalService.numeroSemanaAPlanificar(progreso, hora.hoyEn(progreso.zona()));
        Envio envio = new Envio(command.actorId(), sinRed, "PLAN_SEMANAL_CREAR", "semana:" + semana);
        return entrega.entregar(envio, () -> Aplicado.de(aplicarLaSemana(command, hora, semana)),
                previo -> deLaSemana(command.actorId(), semana));
    }

    private List<RocaSemanal> aplicarLaSemana(CrearPlanSemanalCommand command, RevisionDeLaHora hora, int semana) {
        var maestras = RocasMaestras.de(loadRocaMaestraPort.deParticipante(command.actorId())).exigirCompletas();
        List<RocaSemanal> existentes = deLaSemana(command.actorId(), semana);
        List<RocaSemanal> delPedido = existentes.stream().filter(roca -> maestras.entrySet().stream()
                .anyMatch(e -> e.getValue().id().equals(roca.rocaMaestraId())
                        && command.rocas().stream().anyMatch(item -> item.eje() == e.getKey()))).toList();
        if (delPedido.size() == command.rocas().size()) {
            boolean masNuevo = delPedido.stream().anyMatch(roca -> hora.hayCambioMasNuevo(roca.creadoEn()));
            throw new NoAplicadoSinRedException(masNuevo ? CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR : CodigoNoAplicado.YA_ESTABA_HECHO);
        }
        return crearSemana.crear(command, hora.referencia());
    }

    private List<RocaSemanal> deLaSemana(UserId actor, int semana) {
        List<RocaMaestraId> maestras = loadRocaMaestraPort.deParticipante(actor).stream().map(RocaMaestra::id).toList();
        return loadRocaSemanalPort.deParticipanteYSemana(maestras, semana);
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public RocaSemanal editarSemanal(EditarRocaSemanalCommand command, HechoSinRed sinRed) {
        AccesoARocas.exigir(progresoPort, command.actorId());
        Envio envio = new Envio(command.actorId(), sinRed, "PLAN_SEMANAL_EDITAR", "roca-semanal:" + command.rocaSemanalId().value());
        return entrega.entregar(envio, () -> Aplicado.de(conLaSemanal(command.actorId(), command.rocaSemanalId(), sinRed,
                        hora -> editarSemanal.editar(command, hora.referencia()))),
                previo -> semanalPropia(command.actorId(), command.rocaSemanalId()));
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public RocaSemanal cerrarSemana(CerrarSemanaCommand command, HechoSinRed sinRed) {
        AccesoARocas.exigir(progresoPort, command.actorId());
        Envio envio = new Envio(command.actorId(), sinRed, "PLAN_SEMANAL_CERRAR", "roca-semanal:" + command.rocaSemanalId().value());
        return entrega.entregar(envio, () -> Aplicado.de(conLaSemanal(command.actorId(), command.rocaSemanalId(), sinRed,
                        hora -> cerrarSemana.cerrar(command, hora.referencia()))),
                previo -> semanalPropia(command.actorId(), command.rocaSemanalId()));
    }

    /** La semanal tiene que existir, ser de la persona y no haber cambiado después de lo que hizo; entonces se aplica. */
    private RocaSemanal conLaSemanal(UserId actor, RocaSemanalId id, HechoSinRed sinRed,
                                     java.util.function.Function<RevisionDeLaHora, RocaSemanal> aplicar) {
        RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
        RocaSemanal roca = semanalPropia(actor, id);
        if (hora.hayCambioMasNuevo(roca.actualizadoEn())) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
        return aplicar.apply(hora);
    }

    private RocaSemanal semanalPropia(UserId actor, RocaSemanalId id) {
        RocaSemanal roca = loadRocaSemanalPort.byId(id)
                .orElseThrow(() -> new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));
        boolean esPropia = loadRocaMaestraPort.deParticipante(actor).stream().anyMatch(m -> m.id().equals(roca.rocaMaestraId()));
        if (!esPropia) {
            throw new NotAuthorizedException("Esta roca semanal no pertenece al actor");
        }
        return roca;
    }

    // ─── objetivo de 90 días y del mes ─────────────────────────────────────

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public RocaMaestra definirObjetivoDe90Dias(DefinirRocaMaestraCommand command, HechoSinRed sinRed) {
        AccesoARocas.exigir(progresoPort, command.actorId());
        Envio envio = new Envio(command.actorId(), sinRed, "PLAN_OBJETIVO_90", "maestra:" + command.eje());
        return entrega.entregar(envio, () -> Aplicado.de(aplicarLaMaestra(command, sinRed)),
                previo -> loadRocaMaestraPort.deParticipanteYEje(command.actorId(), command.eje())
                        .orElseThrow(() -> new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE)));
    }

    private RocaMaestra aplicarLaMaestra(DefinirRocaMaestraCommand command, HechoSinRed sinRed) {
        RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
        Predicate<RocaMaestra> cambioDespues = maestra -> !maestra.estaPorConfirmar() && hora.hayCambioMasNuevo(maestra.actualizadoEn());
        if (loadRocaMaestraPort.deParticipanteYEje(command.actorId(), command.eje()).filter(cambioDespues).isPresent()) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
        return definirMaestra.definir(command, hora.referencia());
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public RocaMensual definirObjetivoDelMes(DefinirRocaMensualCommand command, HechoSinRed sinRed) {
        AccesoARocas.exigir(progresoPort, command.actorId());
        Envio envio = new Envio(command.actorId(), sinRed, "PLAN_OBJETIVO_MES", "mensual:" + command.eje() + ":" + command.numeroMes());
        return entrega.entregar(envio, () -> Aplicado.de(aplicarElMes(command, sinRed)),
                previo -> deLaMaestra(command).flatMap(m -> loadRocaMensualPort.deMaestraYMes(m.id(), command.numeroMes()))
                        .orElseThrow(() -> new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE)));
    }

    private RocaMensual aplicarElMes(DefinirRocaMensualCommand command, HechoSinRed sinRed) {
        RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
        RocaMaestra maestra = deLaMaestra(command).orElseThrow(() -> new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));
        Optional<RocaMensual> existente = loadRocaMensualPort.deMaestraYMes(maestra.id(), command.numeroMes());
        if (existente.filter(mes -> hora.hayCambioMasNuevo(mes.actualizadoEn())).isPresent()) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
        return definirMensual.definir(command, hora.referencia());
    }

    private Optional<RocaMaestra> deLaMaestra(DefinirRocaMensualCommand command) {
        return loadRocaMaestraPort.deParticipanteYEje(command.actorId(), command.eje());
    }
}
