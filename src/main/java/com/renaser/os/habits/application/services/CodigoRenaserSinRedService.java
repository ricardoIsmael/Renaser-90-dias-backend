package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarSinRedUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.radar.LoadMiRadarPort;
import com.renaser.os.habits.application.ports.out.radar.LoadRegistroRadarPort;
import com.renaser.os.habits.application.ports.out.radar.SaveRegistroRadarPort;
import com.renaser.os.habits.domain.model.radar.DiaDelRadar;
import com.renaser.os.habits.domain.model.radar.HorarioDelRadar;
import com.renaser.os.habits.domain.model.radar.MiRadar;
import com.renaser.os.habits.domain.model.radar.RegistroRadar;
import com.renaser.os.shared.application.EntregaSinRed;
import com.renaser.os.shared.application.EntregaSinRed.Aplicado;
import com.renaser.os.shared.application.EntregaSinRed.Envio;
import com.renaser.os.shared.application.ports.out.EnviosSinRedPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.RevisionDeLaHora;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * El Código Renaser respondido sin red (spec 048, fase 3, FR-019). Las mismas reglas de {@link RadarService#registrar} corridas con la
 * hora real ({@link RevisionDeLaHora#referencia()}):
 *
 * <ul>
 *   <li>el día del programa a la hora real; desde el 8, Mi Radar encendido, un día con Radar y de 03:00 a 23:59 <b>de esa hora</b>,
 *       con las horas del Despertar y el Dormir de ESA fecha. Si no cabe: {@code MI_RADAR_NO_APLICA}, o
 *       {@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR} si Mi Radar se cambió después de lo que hiciste (D13);</li>
 *   <li>una por hora UTC (índice V52): si esa hora ya tiene su registro, gana el del servidor y se avisa
 *       ({@code YA_ESTABA_HECHO}, P4); nunca dos por hora;</li>
 *   <li>se guarda con {@code creado_en} = la hora real y la hora prevista de esa hora local; sin puntos.</li>
 * </ul>
 *
 * <p>Lo que escribió la persona no se loguea.
 */
@Service
class CodigoRenaserSinRedService implements RegistrarCheckInRadarSinRedUseCase {

    private final AccesoAlRadar acceso;
    private final LoadRegistroRadarPort loadPort;
    private final SaveRegistroRadarPort savePort;
    private final LoadMiRadarPort miRadarPort;
    private final AgendaDelRadar agenda;
    private final EntregaSinRed entrega;
    private final Clock clock;
    private final IdGenerator idGenerator;

    CodigoRenaserSinRedService(AccesoAlRadar acceso, LoadRegistroRadarPort loadPort, SaveRegistroRadarPort savePort,
                               LoadMiRadarPort miRadarPort, AgendaDelRadar agenda, EnviosSinRedPort envios, Clock clock,
                               IdGenerator idGenerator) {
        this.acceso = acceso;
        this.loadPort = loadPort;
        this.savePort = savePort;
        this.miRadarPort = miRadarPort;
        this.agenda = agenda;
        this.entrega = new EntregaSinRed(envios, clock);
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional(noRollbackFor = NoAplicadoSinRedException.class)
    public RegistroRadar registrar(RegistrarCheckInRadarCommand command, HechoSinRed sinRed) {
        ProgresoParticipanteHabits progreso = acceso.propio(command.actorId(), command.participanteId());
        RevisionDeLaHora hora = RevisionDeLaHora.de(sinRed, clock.now());
        Envio envio = new Envio(command.actorId(), sinRed, "CODIGO_RENASER", "radar:" + hora.referencia().truncatedTo(ChronoUnit.HOURS));
        return entrega.entregar(envio, () -> aplicar(command, progreso, hora),
                previo -> deEsaHora(command, hora.referencia()).orElseThrow(() ->
                        new NoSuchElementException("El Código Renaser de ese envío ya no está")));
    }

    private Aplicado<RegistroRadar> aplicar(RegistrarCheckInRadarCommand command, ProgresoParticipanteHabits progreso,
                                            RevisionDeLaHora hora) {
        ZoneId zona = ZoneId.of(progreso.timezone());
        Instant referencia = hora.referencia();
        ZonedDateTime local = referencia.atZone(zona);
        int diaPrograma = hora.diaDelPrograma(progreso.diaPrograma(), zona);
        MiRadar miRadar = HorarioDelRadar.aplicaMiRadar(diaPrograma) ? miRadarPort.deParticipante(command.participanteId())
                : MiRadar.apagado();
        List<Integer> horasDeEseDia = horasDelDia(command, diaPrograma, miRadar, local);
        if (!HorarioDelRadar.puedeRegistrar(diaPrograma, miRadar.encendido(), horasDeEseDia, local)) {
            throw new NoAplicadoSinRedException(miRadarCambioDespues(command, hora)
                    ? CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR : CodigoNoAplicado.MI_RADAR_NO_APLICA);
        }
        if (deEsaHora(command, referencia).isPresent()) {
            throw new NoAplicadoSinRedException(CodigoNoAplicado.YA_ESTABA_HECHO);
        }
        RegistroRadar guardado = savePort.save(RadarService.nuevoRegistro(idGenerator, command, referencia,
                HorarioDelRadar.horaPrevista(diaPrograma, horasDeEseDia, local)));
        return new Aplicado<>(guardado, null, guardado.id().value());
    }

    /** Las horas que pregunta Mi Radar ese día (con el horario de esa fecha); vacío antes del día 8 o con Mi Radar apagado. */
    private List<Integer> horasDelDia(RegistrarCheckInRadarCommand command, int diaPrograma, MiRadar miRadar, ZonedDateTime local) {
        if (!HorarioDelRadar.aplicaMiRadar(diaPrograma) || !miRadar.encendido()) {
            return List.of();
        }
        DiaDelRadar dia = agenda.delDia(command.participanteId(), miRadar, local.toLocalDate());
        return dia.horas();
    }

    private boolean miRadarCambioDespues(RegistrarCheckInRadarCommand command, RevisionDeLaHora hora) {
        return miRadarPort.actualizadoEn(command.participanteId()).filter(hora::hayCambioMasNuevo).isPresent();
    }

    /** Cualquier registro de esa hora UTC (la misma que parte el índice único de V52). */
    private java.util.Optional<RegistroRadar> deEsaHora(RegistrarCheckInRadarCommand command, Instant referencia) {
        Instant desde = referencia.truncatedTo(ChronoUnit.HOURS);
        return loadPort.entre(command.participanteId(), desde, desde.plus(1, ChronoUnit.HOURS)).stream().findFirst();
    }
}
