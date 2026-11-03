package com.renaser.os.habits.application.services;

import com.renaser.os.habits.api.AgendaDelDiaFinder;
import com.renaser.os.habits.api.HabitoEnJuegoResumen;
import com.renaser.os.habits.api.HabitoEnJuegoResumen.TramoPuntos;
import com.renaser.os.habits.api.MedicionDelHabito;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.MedicionDelTrack;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.TrackDelDiaConCatalogo;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.medicion.ContarMedicionesAutomaticasPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.registro.PuntosEnJuego;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.habits.domain.model.registro.ResultadoOtorgamiento;
import com.renaser.os.habits.domain.model.registro.VentanaEntrega;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementa la cara publica de `habits` hacia otros modulos (hoy, las herramientas del agente
 * de `rag`). Mismo patron exacto que {@link HabitosDelDiaFinderService}: una fachada delgada
 * sobre casos de uso ya probados, sin consultas ni reglas propias.
 *
 * <p>Que sea delgado es el punto. Todo lo que importa — que dia es hoy para esa persona, cuanto
 * paga el habito, si todavia se puede entregar, si el actor es su dueno — ya esta resuelto y
 * probado en {@code ConsultarTracksDelDiaConCatalogoUseCase} y {@code CompletarRegistroUseCase}.
 * Si esta clase creciera con logica propia, seria una segunda copia de esas reglas.
 */
@Service
public class AgendaDelDiaFinderService implements AgendaDelDiaFinder {

    private final ConsultarTracksDelDiaConCatalogoUseCase consultarTracksUseCase;
    private final CompletarRegistroUseCase completarRegistroUseCase;
    private final ConsultarProgresoParticipanteHabitsPort progresoPort;
    private final LoadHabitoPort loadHabitoPort;
    private final ContarMedicionesAutomaticasPort medicionesAutomaticasPort;

    public AgendaDelDiaFinderService(ConsultarTracksDelDiaConCatalogoUseCase consultarTracksUseCase,
                                      CompletarRegistroUseCase completarRegistroUseCase,
                                      ConsultarProgresoParticipanteHabitsPort progresoPort,
                                      LoadHabitoPort loadHabitoPort,
                                      ContarMedicionesAutomaticasPort medicionesAutomaticasPort) {
        this.consultarTracksUseCase = consultarTracksUseCase;
        this.completarRegistroUseCase = completarRegistroUseCase;
        this.progresoPort = progresoPort;
        this.loadHabitoPort = loadHabitoPort;
        this.medicionesAutomaticasPort = medicionesAutomaticasPort;
    }

    @Override
    public List<HabitoEnJuegoResumen> deHoyDe(UserId participanteId) {
        List<TrackDelDiaConCatalogo> tracks = consultarTracksUseCase.consultarHoyDe(participanteId);
        Map<HabitoId, String> dimensiones = dimensionesDe(tracks);
        return tracks.stream()
                .map(vista -> resumirDe(vista, dimensiones.get(vista.registro().habitoId()),
                        medicionDe(participanteId, vista)))
                .toList();
    }

    /**
     * Spec 037 sobre la 038 (T021): la medicion de un habito medible, con la pista de si ya mide con el telefono. Solo el habito medible (hoy uno) cuesta
     * una consulta; el resto de la agenda no cambia. Si esa consulta falla, la medicion sale sin la pista: SER vuelve a lo de siempre (la camara) en vez
     * de caerse.
     */
    private MedicionDelHabito medicionDe(UserId participanteId, TrackDelDiaConCatalogo vista) {
        MedicionDelTrack medicion = vista.medicion();
        if (medicion == null) {
            return null;
        }
        return new MedicionDelHabito(medicion.unidad().name(), medicion.meta(), medicion.valorDelDia(),
                medicion.origenDelDia() == null ? null : medicion.origenDelDia().name(), medicion.fuenteDelDia(),
                conPasosConectados(participanteId, vista));
    }

    private boolean conPasosConectados(UserId participanteId, TrackDelDiaConCatalogo vista) {
        if (vista.claveSistema() == null) {
            return false;
        }
        try {
            Set<UserId> automaticas = medicionesAutomaticasPort.conAlgunaMedicionAutomatica(List.of(participanteId),
                    vista.claveSistema(), vista.registro().fechaEjecucion());
            return automaticas.contains(participanteId);
        } catch (RuntimeException sinLeer) {
            return false;
        }
    }

    /** E-455: la dimension de cada habito del dia, en UNA consulta para todo el dia. */
    private Map<HabitoId, String> dimensionesDe(List<TrackDelDiaConCatalogo> tracks) {
        if (tracks.isEmpty()) {
            return Map.of();
        }
        return loadHabitoPort.porIds(tracks.stream().map(vista -> vista.registro().habitoId()).collect(Collectors.toSet()))
                .stream()
                .filter(habito -> habito.categoriaClave() != null)
                .collect(Collectors.toMap(Habito::id, Habito::categoriaClave, (una, otra) -> una));
    }

    @Override
    public int completar(UserId actorId, UUID registroId) {
        return completarRegistroUseCase.completar(new CompletarRegistroCommand(actorId,
                RegistroHabitoId.of(registroId), null, null)).puntosOtorgados();
    }

    /** Mismo respaldo a UTC que {@code TracksDelDiaProyeccionService.momentoDe}: la zona de los plazos. */
    @Override
    public ZoneId zonaDe(UserId participanteId) {
        return progresoPort.deParticipante(participanteId)
                .map(progreso -> ZoneId.of(progreso.timezone()))
                .orElse(ZoneId.of("UTC"));
    }

    private static HabitoEnJuegoResumen resumirDe(TrackDelDiaConCatalogo vista, String categoriaClave, MedicionDelHabito medicion) {
        PuntosEnJuego enJuego = vista.puntosEnJuego();
        return new HabitoEnJuegoResumen(vista.registro().id().value(), vista.tituloHabito(),
                vista.registro().estado().name(), enJuego != null ? enJuego.siCompletaAhora() : null,
                enJuego != null ? enJuego.maximo() : null, enJuego != null ? enJuego.plazo() : null,
                vista.exigeEvidencia(), tramosDe(enJuego), vista.claveSistema(), vista.tituloDelPrograma(),
                vista.horaDisparo(), categoriaClave, vista.registro().habitoId().value(), vista.registro().fechaEjecucion(), medicion);
    }

    /**
     * La escala de D-97 de este registro, tramo por tramo, calculada por
     * {@link ResultadoOtorgamiento} — la unica fuente del puntaje — y no reescrita aca.
     *
     * <p>Como se llega sin conocer la ventana entera: {@link VentanaEntrega} define
     * {@code plazo = ancla + extension + GRACIA_MINUTOS}, asi que la gracia empieza en
     * {@code plazo - GRACIA_MINUTOS}. Y {@code ResultadoOtorgamiento.calcular} depende solo de
     * cuanto se paso la entrega de {@code ancla + extension}: evaluarlo con ese instante como
     * ancla y extension cero da, para cualquier entrega, el mismo puntaje que con la ventana
     * original. Antes de la gracia paga siempre el maximo (a tiempo o en extension).
     *
     * <p>Dentro de la gracia el puntaje solo cambia en minutos enteros ({@code calcular} trunca
     * los minutos transcurridos), por eso se lo evalua al inicio de cada minuto y los minutos
     * consecutivos con el mismo puntaje se funden en un tramo. {@code AgendaDelDiaFinderServiceTest}
     * compara el resultado contra {@code PuntosEnJuego.de} con una ventana real, cada 30 segundos:
     * si la escala cambia de forma, ese test lo dice.
     */
    static List<TramoPuntos> tramosDe(PuntosEnJuego enJuego) {
        if (enJuego == null || enJuego.plazo() == null) {
            return List.of();
        }
        Instant inicioGracia = enJuego.plazo().minus(Duration.ofMinutes(VentanaEntrega.GRACIA_MINUTOS));
        List<TramoPuntos> tramos = new ArrayList<>();
        for (int minuto = 0; minuto < VentanaEntrega.GRACIA_MINUTOS; minuto++) {
            Instant desde = inicioGracia.plus(Duration.ofMinutes(minuto));
            int puntos = ResultadoOtorgamiento.calcular(inicioGracia, desde, Duration.ZERO).puntos();
            extenderOAgregar(tramos, new TramoPuntos(desde.plus(Duration.ofMinutes(1)), puntos));
        }
        return List.copyOf(tramos);
    }

    /** Un minuto que paga lo mismo que el tramo anterior lo alarga; uno que paga distinto abre otro. */
    private static void extenderOAgregar(List<TramoPuntos> tramos, TramoPuntos minuto) {
        int ultimo = tramos.size() - 1;
        if (ultimo >= 0 && tramos.get(ultimo).puntos() == minuto.puntos()) {
            tramos.set(ultimo, minuto);
        } else {
            tramos.add(minuto);
        }
    }
}
