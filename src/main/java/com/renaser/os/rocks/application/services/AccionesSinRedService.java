package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocadiaria.AccionesSinRedUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CompletarRocaDiariaUseCase.CompletarRocaDiariaCommand;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase;
import com.renaser.os.rocks.application.ports.in.rocadiaria.CrearPlanDiarioUseCase.CrearPlanDiarioCommand;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.rocadiaria.LoadRocaDiariaPort;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import com.renaser.os.shared.application.EntregaSinRed;
import com.renaser.os.shared.application.EntregaSinRed.Aplicado;
import com.renaser.os.shared.application.EntregaSinRed.Envio;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort.EnvioRegistrado;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.MotivoSinPuntos;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.RevisionDeLaHora;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Las acciones del día hechas sin red (spec 048, fase 3, FR-019): agendarlas y marcarlas. Las reglas de {@code RocaDiariaService}
 * corridas con la hora real ({@link RevisionDeLaHora#referencia()}); aquí solo se decide si se aplica.
 *
 * <p><b>Agendar.</b> Un día que ya pasó al llegar no se aplica ({@code FUERA_DE_SU_DIA}, P2: crear acciones en un día vivido distorsiona
 * el avance de la semana). Un día ya armado cuyas acciones cambiaron después de lo que hiciste, tampoco ({@code
 * CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}: una acción cumplida cuenta como cambio); un día futuro cuyas acciones son anteriores se reemplaza, como
 * con red (E-208); el día en curso ya armado es {@code YA_ESTABA_HECHO}.
 *
 * <p><b>Marcar.</b> Es lo único de la fase que da puntos, y nunca rebota (D14): hora futura, llegada a más de 48 h o una acción que no era
 * del día de la hora real la dejan cumplida y sin puntos, con su motivo. Ya cumplida: 200 con {@code YA_ESTABA_HECHO}. Una acción que ya
 * no existe es {@code RECURSO_YA_NO_EXISTE}. La Ley IV (la VERDE primero) y el EXIF siguen como con red.
 */
@Service
class AccionesSinRedService implements AccionesSinRedUseCase {

    private final ConsultarProgresoParticipanteRocksPort progresoPort;
    private final LoadRocaDiariaPort loadRocaDiariaPort;
    private final CrearPlanDiarioUseCase crear;
    private final CompletarRocaDiariaUseCase completar;
    private final EntregaSinRed entrega;
    private final Clock clock;

    AccionesSinRedService(ConsultarProgresoParticipanteRocksPort progresoPort, LoadRocaDiariaPort loadRocaDiariaPort,
                          CrearPlanDiarioUseCase crear, CompletarRocaDiariaUseCase completar, EnviosSinRedPort envios, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadRocaDiariaPort = loadRocaDiariaPort;
        this.crear = crear;
        this.completar = completar;
        this.entrega = new EntregaSinRed(envios, clock);
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public List<RocaDiaria> agendar(CrearPlanDiarioCommand command, HechoSinRed sinRed) {
        ProgresoParticipanteRocks progreso = AccesoARocas.exigir(progresoPort, command.actorId());
        Envio envio = new Envio(command.actorId(), sinRed, "PLAN_AGENDAR", "plan-diario:" + command.fecha());
        return entrega.entregar(envio, () -> Aplicado.de(aplicarLaAgenda(command, sinRed, progreso.zona())),
                previo -> loadRocaDiariaPort.deParticipanteYFecha(command.actorId(), command.fecha()));
    }

    private List<RocaDiaria> aplicarLaAgenda(CrearPlanDiarioCommand command, HechoSinRed sinRed, ZoneId zona) {
        RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
        if (command.fecha().isBefore(hora.hoyDeLaLlegadaEn(zona))) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.FUERA_DE_SU_DIA);
        }
        List<RocaDiaria> delDia = loadRocaDiariaPort.deParticipanteYFecha(command.actorId(), command.fecha());
        if (delDia.stream().anyMatch(roca -> hora.hayCambioMasNuevo(roca.actualizadoEn()))) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
        if (!delDia.isEmpty() && !command.fecha().isAfter(hora.hoyEn(zona))) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.YA_ESTABA_HECHO);
        }
        return crear.crear(command, hora.referencia());
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public AccionMarcada marcar(CompletarRocaDiariaCommand command, HechoSinRed sinRed) {
        ProgresoParticipanteRocks progreso = AccesoARocas.exigir(progresoPort, command.actorId());
        Envio envio = new Envio(command.actorId(), sinRed, "PLAN_MARCAR_ACCION", "roca-diaria:" + command.rocaDiariaId().value());
        return entrega.entregar(envio, () -> aplicarLaMarca(command, sinRed, progreso.zona()),
                previo -> releerLaMarca(command, previo));
    }

    private Aplicado<AccionMarcada> aplicarLaMarca(CompletarRocaDiariaCommand command, HechoSinRed sinRed, ZoneId zona) {
        RocaDiaria roca = loadRocaDiariaPort.byId(command.rocaDiariaId())
                .orElseThrow(() -> new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));
        if (!roca.participanteId().equals(command.actorId())) {
            throw new NotAuthorizedException("Esta roca diaria no pertenece al actor");
        }
        if (roca.completada()) {
            return Aplicado.sinPuntos(new AccionMarcada(roca, MotivoSinPuntos.YA_ESTABA_HECHO), MotivoSinPuntos.YA_ESTABA_HECHO.name());
        }
        RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
        Optional<MotivoSinPuntos> motivo = hora.motivoSinPuntos(roca.fecha(), zona);
        RocaDiaria hecha = completar.completarConHoraReal(command, hora.referencia(), motivo.isEmpty());
        return Aplicado.sinPuntos(new AccionMarcada(hecha, motivo.orElse(null)), motivo.map(Enum::name).orElse(null));
    }

    /** Un reintento de lo ya aplicado: la acción como quedó, con el mismo motivo que la primera vez. */
    private AccionMarcada releerLaMarca(CompletarRocaDiariaCommand command, EnvioRegistrado previo) {
        RocaDiaria roca = loadRocaDiariaPort.byId(command.rocaDiariaId())
                .orElseThrow(() -> new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE));
        MotivoSinPuntos motivo = previo.motivoSinPuntos() == null ? null : MotivoSinPuntos.valueOf(previo.motivoSinPuntos());
        return new AccionMarcada(roca, motivo);
    }
}
