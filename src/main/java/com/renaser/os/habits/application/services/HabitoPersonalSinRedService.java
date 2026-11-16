package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase;
import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.habits.application.ports.in.habito.HabitoPersonalSinRedUseCase;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.shared.application.EntregaSinRed;
import com.renaser.os.shared.application.EntregaSinRed.Aplicado;
import com.renaser.os.shared.application.EntregaSinRed.Envio;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crear un hábito personal sin red (spec 048, fase 3, FR-019). Es un alta: no choca con nada (la persona lo inventa) y es idempotente por
 * {@code idCliente}: un reintento devuelve el mismo hábito, sin duplicarlo. El id del hábito queda en el rastro del envío para releerlo.
 */
@Service
class HabitoPersonalSinRedService implements HabitoPersonalSinRedUseCase {

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final CrearHabitoPersonalUseCase crear;
    private final EntregaSinRed entrega;

    HabitoPersonalSinRedService(ConsultarProgresoParticipanteHabitsPort progresoPort, LoadHabitoPort loadHabitoPort,
                                CrearHabitoPersonalUseCase crear, EnviosSinRedPort envios, Clock clock) {
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.crear = crear;
        this.entrega = new EntregaSinRed(envios, clock);
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public Habito crear(CrearHabitoPersonalCommand command, HechoSinRed sinRed) {
        AccesoAlPlanDeHabitos.exigir(progresoPort, command.actorId());
        Envio envio = new Envio(command.actorId(), sinRed, "HABITO_PERSONAL", "habito:" + sinRed.idCliente());
        return entrega.entregar(envio, () -> {
            Habito creado = crear.crear(command);
            return new Aplicado<>(creado, null, creado.id().value());
        }, previo -> loadHabitoPort.byId(HabitoId.of(previo.registroId()))
                .orElseThrow(() -> new NoAplicadoSinRedException(CodigoNoAplicado.RECURSO_YA_NO_EXISTE)));
    }
}
