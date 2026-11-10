package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarSinRedUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.ConfigurarMiRadarCommand;
import com.renaser.os.habits.application.ports.in.radar.ConsultarMiRadarUseCase;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.domain.model.radar.PlanDeMiRadar;
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

/**
 * Mi Radar guardado sin red (spec 048, fase 3, FR-019). Mi Radar es UNA cosa (encendido y días se guardan juntos): si
 * {@code mi_radar_actualizado_en} (V191) es posterior a {@code hechoEn}, no se aplica y gana el servidor
 * ({@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}, D13). Si no, se aplica con las reglas de siempre corridas con la hora real
 * («hoy y lo pasado no se editan») y se deja su hora lógica en esa hora.
 */
@Service
class MiRadarSinRedService implements ConfigurarMiRadarSinRedUseCase {

    private final AccesoAlRadar acceso;
    private final LoadMiRadarPort miRadarPort;
    private final ConfigurarMiRadarUseCase configurar;
    private final ConsultarMiRadarUseCase consultar;
    private final EntregaSinRed entrega;
    private final Clock clock;

    MiRadarSinRedService(AccesoAlRadar acceso, LoadMiRadarPort miRadarPort, ConfigurarMiRadarUseCase configurar,
                         ConsultarMiRadarUseCase consultar, EnviosSinRedPort envios, Clock clock) {
        this.acceso = acceso;
        this.miRadarPort = miRadarPort;
        this.configurar = configurar;
        this.consultar = consultar;
        this.entrega = new EntregaSinRed(envios, clock);
        this.clock = clock;
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public PlanDeMiRadar configurar(ConfigurarMiRadarCommand command, HechoSinRed sinRed) {
        acceso.propio(command.actorId(), command.participanteId());
        Envio envio = new Envio(command.actorId(), sinRed, "MI_RADAR", "mi-radar");
        return entrega.entregar(envio, () -> aplicar(command, sinRed),
                previo -> consultar.consultar(command.actorId(), command.participanteId()));
    }

    private Aplicado<PlanDeMiRadar> aplicar(ConfigurarMiRadarCommand command, HechoSinRed sinRed) {
        RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
        if (miRadarPort.actualizadoEn(command.participanteId()).filter(hora::hayCambioMasNuevo).isPresent()) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR);
        }
        return Aplicado.de(configurar.configurar(command, hora.referencia()));
    }
}
