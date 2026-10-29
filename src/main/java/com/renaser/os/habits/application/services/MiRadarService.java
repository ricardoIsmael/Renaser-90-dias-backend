package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConsultarMiRadarUseCase;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveMiRadarPort;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mi Radar (spec 012, D-286): encender, apagar y cambiar las horas (de todos los dias y por dia de la semana), y leer
 * como esta. Solo la propia persona y con el programa andando, con la misma puerta que el resto del Codigo Renaser
 * ({@code AccesoAlRadar}).
 *
 * <p>No mira el dia del programa: la app ofrece Mi Radar desde el dia 8 y es el registro ({@code RadarService}) el que
 * decide si se puede responder. La spec no pide cerrar este guardado antes y no se inventa esa regla. Nada de lo que
 * se guarda se loguea.
 */
@Service
public class MiRadarService implements ConsultarMiRadarUseCase, ConfigurarMiRadarUseCase {

    private final AccesoAlRadar acceso;
    private final LoadMiRadarPort loadPort;
    private final SaveMiRadarPort savePort;

    MiRadarService(AccesoAlRadar acceso, LoadMiRadarPort loadPort, SaveMiRadarPort savePort) {
        this.acceso = acceso;
        this.loadPort = loadPort;
        this.savePort = savePort;
    }

    @Override
    public MiRadar consultar(UserId actorId, UserId participanteId) {
        acceso.propio(actorId, participanteId);
        return loadPort.deParticipante(participanteId);
    }

    @Override
    @Transactional
    public MiRadar configurar(ConfigurarMiRadarCommand command) {
        acceso.propio(command.actorId(), command.participanteId());
        // Apagar ignora las horas y los dias que lleguen (contrato); encender los valida el dominio.
        MiRadar miRadar = command.encendido()
                ? MiRadar.encendido(command.horas(), command.dias()) : MiRadar.apagado();
        savePort.guardar(command.participanteId(), miRadar);
        return miRadar;
    }
}
