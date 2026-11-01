package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.RespuestasDelRadarFinder;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

/** {@link RespuestasDelRadarFinder} sobre el puerto de lectura de registros: traduce el registro del dominio al contrato. No autoriza. */
@Service
class RespuestasDelRadarService implements RespuestasDelRadarFinder {

    private final LoadRegistroRadarPort registros;

    RespuestasDelRadarService(LoadRegistroRadarPort registros) {
        this.registros = registros;
    }

    @Override
    public List<RespuestaDelRadar> entre(UserId personaId, Instant desde, Instant hasta) {
        return registros.entre(personaId, desde, hasta).stream().map(RespuestasDelRadarService::aContrato).toList();
    }

    private static RespuestaDelRadar aContrato(RegistroRadar registro) {
        return new RespuestaDelRadar(registro.creadoEn(), registro.horaPrevista(), registro.nivelEnergia(), registro.queHago(),
                registro.quePienso(), registro.queSiento(), registro.queEvito(), registro.dictado());
    }
}
