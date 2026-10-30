package com.renaser.os.habits.infrastructure.adapter.out.persistence.radar;

import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveMiRadarPort;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar.DiaPropio;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.HorasDeMiRadarPort;
import com.renaser.os.users.api.HorasDeMiRadarPort.HorasDeMiRadar;
import com.renaser.os.users.api.HorasDeMiRadarPort.HorasDeUnDia;
import org.springframework.stereotype.Component;

/**
 * Mi Radar vive en {@code participantes_programa.horas_mi_radar} (las horas de todos los dias) y
 * {@code dias_mi_radar} (los dias con horas propias), V119: una tabla de {@code users}, asi que se llega por su
 * contrato publico y no con SQL propio (D-41, regla 01). Traduce lo guardado a {@link MiRadar}, que es quien lo valida;
 * sin horas guardadas es apagado.
 */
@Component
class MiRadarPersistenceAdapter implements LoadMiRadarPort, SaveMiRadarPort {

    private final HorasDeMiRadarPort horas;

    MiRadarPersistenceAdapter(HorasDeMiRadarPort horas) {
        this.horas = horas;
    }

    @Override
    public MiRadar deParticipante(UserId participanteId) {
        HorasDeMiRadar guardado = horas.horasDe(participanteId);
        return new MiRadar(guardado.horas(),
                guardado.dias().stream().map(dia -> new DiaPropio(dia.dia(), dia.horas())).toList());
    }

    @Override
    public void guardar(UserId participanteId, MiRadar miRadar) {
        horas.guardarHoras(participanteId, new HorasDeMiRadar(miRadar.horas(),
                miRadar.dias().stream().map(dia -> new HorasDeUnDia(dia.dia(), dia.horas())).toList()));
    }
}
