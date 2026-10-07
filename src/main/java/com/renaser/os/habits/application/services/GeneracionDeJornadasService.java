package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.preferencia.PromoverCambiosHorarioProgramadosUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarJornadasDelDiaUseCase;
import com.renaser.os.habits.application.ports.in.registro.GenerarTracksDelDiaUseCase;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.registro.ConsultarJornadasGeneradasPort;
import com.renaser.os.habits.domain.model.registro.JornadaDelDia;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * E-556 — el barrido que arma el dia de cada participante cuando empieza SU dia ({@link JornadaDelDia}). Antes corria
 * una vez, a las 05:02 UTC (la medianoche de Lima), y le generaba a cada quien "el dia de su zona" a esa hora: para
 * alguien en Los Angeles era las 21:02 de la vispera, para alguien en Tokio las 14:02 del dia mismo.
 *
 * <p><b>Regla 02 §4, punto por punto.</b> Corre cada hora y el dominio decide con quien hay algo que hacer: quien ya
 * tiene registros de su hoy no se toca, asi que una hora en que a nadie le empezo el dia no escribe nada. Recorre el
 * padron de a {@value #TAMANO_LOTE} (una consulta de zonas y una de "ya tiene su dia" por lote). Sin
 * {@code @Transactional} sobre el barrido: cada caso de uso abre la suya por participante, y un {@code try/catch} por
 * participante hace que un fallo quede para la proxima hora sin frenar a los demas.
 *
 * <p><b>Idempotente y atrasado-tolerante (regla 02 §2).</b> Es una funcion de "este participante, en su zona, a esta
 * hora, todavia no tiene su dia": correrlo dos veces o tarde da lo mismo. Si llega dentro de las dos primeras horas del
 * dia local arma la jornada completa, como el barrido de las 05:02 UTC para Lima; si llega mas tarde (backend caido),
 * la arma como cuando la persona abre la app ({@code generarDisponiblesAhora}): desde D-259 tambien completa, salvo en
 * su primer dia del programa. <i>Corregido 2026-10-06: decia «arma lo que todavia se puede completar».</i>
 *
 * <p><b>El orden con la promocion de cambios de horario es por participante, no por cron.</b> Antes de generarle el dia
 * se hacen regir sus cambios de horario que le tocan hoy: esa pareja no depende de que un cron corra antes que otro
 * (la causa de E-91 era un margen de diez minutos entre dos crons). Quien ya tenia su dia armado antes de que un cambio
 * le rigiera lo recibe del barrido de promocion, y el horario nuevo vale para la jornada siguiente.
 *
 * <p><b>Costo conocido:</b> quien no tenga ningun habito que le aplique ese dia no deja registros, asi que se le
 * reintenta cada hora hasta que termina su dia. Es lo mismo que ya pasaba cada vez que abria la app, y la corrida no
 * escribe nada para el.
 */
@Service
public class GeneracionDeJornadasService implements GenerarJornadasDelDiaUseCase {

    private static final Logger log = LoggerFactory.getLogger(GeneracionDeJornadasService.class);

    /** Participantes por lote: una consulta de zonas y una de "ya tiene su dia" por cada uno. */
    static final int TAMANO_LOTE = 200;

    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final ConsultarJornadasGeneradasPort jornadasPort;
    private final ZonasDelPadron zonas;
    private final PromoverCambiosHorarioProgramadosUseCase promoverCambios;
    private final GenerarTracksDelDiaUseCase generarTracks;
    private final Clock clock;

    public GeneracionDeJornadasService(ConsultarProgresoParticipanteHabitsPort progresoPort,
                                       ConsultarJornadasGeneradasPort jornadasPort, ZonasDelPadron zonas,
                                       PromoverCambiosHorarioProgramadosUseCase promoverCambios,
                                       GenerarTracksDelDiaUseCase generarTracks, Clock clock) {
        this.progresoPort = progresoPort;
        this.jornadasPort = jornadasPort;
        this.zonas = zonas;
        this.promoverCambios = promoverCambios;
        this.generarTracks = generarTracks;
        this.clock = clock;
    }

    @Override
    public ResultadoDelBarrido generarLasQueYaEmpezaron() {
        Instant ahora = clock.now();
        List<UserId> padron = progresoPort.participantesInscritosActivos();
        ResultadoDelBarrido total = ResultadoDelBarrido.VACIO;
        for (int desde = 0; desde < padron.size(); desde += TAMANO_LOTE) {
            List<UserId> lote = padron.subList(desde, Math.min(desde + TAMANO_LOTE, padron.size()));
            total = total.mas(procesar(lote, ahora));
        }
        return total;
    }

    private ResultadoDelBarrido procesar(List<UserId> lote, Instant ahora) {
        Map<UserId, ZoneId> zonasDelLote = zonas.leerLote(lote);
        Map<UserId, JornadaDelDia> jornadas = new LinkedHashMap<>();
        int fallidos = 0;
        for (UserId participante : lote) {
            try {
                jornadas.put(participante, JornadaDelDia.de(zonas.de(participante, zonasDelLote), ahora));
            } catch (RuntimeException ex) {
                fallidos++;
                log.warn("[habits] no se pudo saber el dia de {} (se reintenta la proxima hora): {}", participante,
                        ex.toString());
            }
        }
        Set<UserId> conSuDia = conSuDiaYaArmado(jornadas);
        ResultadoDelBarrido generado = generarLosQueFaltan(jornadas, conSuDia);
        return generado.mas(new ResultadoDelBarrido(lote.size(), 0, fallidos));
    }

    /** Una consulta por fecha distinta del lote (a lo sumo tres a la vez: el mundo abarca 26 horas de offset). */
    private Set<UserId> conSuDiaYaArmado(Map<UserId, JornadaDelDia> jornadas) {
        Map<LocalDate, List<UserId>> porFecha = jornadas.entrySet().stream().collect(
                Collectors.groupingBy(e -> e.getValue().hoy(),
                        Collectors.mapping(Map.Entry::getKey, Collectors.toList())));
        Set<UserId> conSuDia = new HashSet<>();
        porFecha.forEach((fecha, gente) -> conSuDia.addAll(conRegistros(gente, fecha)));
        return conSuDia;
    }

    /**
     * Si la consulta falla se asume que nadie lo tiene: generar es idempotente, asi que lo peor es trabajo de mas
     * esta hora, no un dia duplicado.
     */
    private Set<UserId> conRegistros(List<UserId> gente, LocalDate fecha) {
        try {
            return jornadasPort.conRegistrosEn(gente, fecha);
        } catch (RuntimeException ex) {
            log.warn("[habits] no se pudo saber quien ya tiene su dia del {} ({} participante(s)); se genera igual: {}",
                    fecha, gente.size(), ex.toString());
            return Set.of();
        }
    }

    private ResultadoDelBarrido generarLosQueFaltan(Map<UserId, JornadaDelDia> jornadas, Set<UserId> conSuDia) {
        int generados = 0;
        int fallidos = 0;
        for (Map.Entry<UserId, JornadaDelDia> entrada : jornadas.entrySet()) {
            if (conSuDia.contains(entrada.getKey())) {
                continue;
            }
            if (armarSuDia(entrada.getKey(), entrada.getValue())) {
                generados++;
            } else {
                fallidos++;
            }
        }
        return new ResultadoDelBarrido(0, generados, fallidos);
    }

    /** {@code false} si falla (suspendido, datos corruptos): queda para la proxima hora y los demas siguen. */
    private boolean armarSuDia(UserId participante, JornadaDelDia jornada) {
        try {
            promoverCambios.promoverLosDe(participante, jornada.hoy());
            if (jornada.acabaDeEmpezar()) {
                generarTracks.generarDiaCompletoEnSuZona(participante);
            } else {
                generarTracks.generarDisponiblesAhora(participante);
            }
            return true;
        } catch (RuntimeException ex) {
            log.warn("[habits] no se pudo armar el dia de {} (se reintenta la proxima hora): {}", participante,
                    ex.toString());
            return false;
        }
    }
}
