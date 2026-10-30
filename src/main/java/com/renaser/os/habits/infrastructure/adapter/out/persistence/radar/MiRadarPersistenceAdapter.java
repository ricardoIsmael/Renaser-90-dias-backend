package com.renaser.os.habits.infrastructure.adapter.out.persistence.radar;

import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveMiRadarPort;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar;
import com.renaser.os.habits.domain.model.radar.DiaDeMiRadar.Modo;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.MiRadarGuardadoPort;
import com.renaser.os.users.api.MiRadarGuardadoPort.DiaGuardado;
import com.renaser.os.users.api.MiRadarGuardadoPort.MiRadarGuardado;
import org.springframework.stereotype.Component;

/**
 * Mi Radar vive en {@code participantes_programa.mi_radar_encendido} y {@code dias_mi_radar} (V122): una tabla de
 * {@code users}, asi que se llega por su contrato publico y no con SQL propio (D-41, regla 01). Traduce lo guardado a
 * {@link MiRadar}, que es quien lo valida; sin nada guardado es apagado y con todos los dias de fabrica.
 */
@Component
class MiRadarPersistenceAdapter implements LoadMiRadarPort, SaveMiRadarPort {

    private final MiRadarGuardadoPort guardado;

    MiRadarPersistenceAdapter(MiRadarGuardadoPort guardado) {
        this.guardado = guardado;
    }

    @Override
    public MiRadar deParticipante(UserId participanteId) {
        MiRadarGuardado leido = guardado.de(participanteId);
        return new MiRadar(leido.encendido(), leido.dias().stream()
                .map(dia -> new DiaDeMiRadar(dia.dia(), Modo.valueOf(dia.modo()), dia.veces(), dia.sinRadar()))
                .toList());
    }

    @Override
    public void guardar(UserId participanteId, MiRadar miRadar) {
        guardado.guardar(participanteId, new MiRadarGuardado(miRadar.encendido(), miRadar.dias().stream()
                .map(dia -> new DiaGuardado(dia.dia(), dia.modo().name(), dia.veces(), dia.sinRadar())).toList()));
    }
}
