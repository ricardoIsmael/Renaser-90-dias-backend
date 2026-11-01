package com.renaser.os.rag.application.services.analisis;

import com.renaser.os.rag.application.ports.in.analisis.GenerarAnalisis8020UseCase;
import com.renaser.os.rag.application.ports.in.analisis.GenerarAnalisisPendientesUseCase;
import com.renaser.os.rag.application.ports.out.analisis.LoadAnalisisDelPeriodoPort;
import com.renaser.os.rag.application.ports.out.analisis.LoadAnalisisDelPeriodoPort.EstadoDeUnPeriodo;
import com.renaser.os.rag.application.services.circuito.InterruptoresDeSer;
import com.renaser.os.rag.domain.model.analisis.ReglasDelAnalisis;
import com.renaser.os.rag.domain.model.analisis.TipoDePeriodo;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder;
import com.renaser.os.rocks.api.PeriodosDelProgramaFinder.PeriodoDelPrograma;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El barrido horario del análisis 80/20 (spec 031, FR-005 y FR-018). No corre «el lunes a una hora»: corre cada hora y, con el día de cada
 * persona en SU zona, decide quién tiene un periodo que cerró (regla 02 §1: la medianoche local no cae a una hora UTC fija). Un periodo
 * cerrado entra en la ventana de puesta al día ({@link ReglasDelAnalisis#diasDePuestaAlDia()}): un servidor caído toda una noche, la IA
 * apagada o un tope del día alcanzado no pierden el análisis, y pasada la ventana se deja de intentar (FR-016).
 *
 * <p>Sin {@code @Transactional} (regla 02 §4): cada persona se genera sola y una que falla no detiene el barrido. El padrón se recorre por
 * lotes: los programas y lo ya decidido se piden en UNA consulta por lote, no una por persona. Con {@code ia.analisis} apagada no corre.
 */
@Service
public class BarridoDeAnalisis8020Service implements GenerarAnalisisPendientesUseCase {

    private static final Logger log = LoggerFactory.getLogger(BarridoDeAnalisis8020Service.class);

    static final int TAMANO_LOTE = 200;

    private final ParticipacionProgramaFinder participaciones;
    private final ProgramasActivadosFinder programas;
    private final PeriodosDelProgramaFinder periodos;
    private final LoadAnalisisDelPeriodoPort decididos;
    private final GenerarAnalisis8020UseCase generar;
    private final InterruptoresDeSer interruptores;
    private final ReglasDelAnalisis reglas;
    private final Clock clock;

    BarridoDeAnalisis8020Service(ParticipacionProgramaFinder participaciones, ProgramasActivadosFinder programas,
                                 PeriodosDelProgramaFinder periodos, LoadAnalisisDelPeriodoPort decididos,
                                 GenerarAnalisis8020UseCase generar, InterruptoresDeSer interruptores, ReglasDelAnalisis reglas,
                                 Clock clock) {
        this.participaciones = participaciones;
        this.programas = programas;
        this.periodos = periodos;
        this.decididos = decididos;
        this.generar = generar;
        this.interruptores = interruptores;
        this.reglas = reglas;
        this.clock = clock;
    }

    @Override
    public void generarLosQueTocan() {
        if (!interruptores.analisisPrendido()) {
            log.info("[rag.BarridoDeAnalisis80_20] ia.analisis está apagada; el barrido no corre este turno");
            return;
        }
        Instant ahora = clock.now();
        List<UserId> padron = participaciones.participantesInscritosActivos();
        for (int desde = 0; desde < padron.size(); desde += TAMANO_LOTE) {
            recorrer(padron.subList(desde, Math.min(desde + TAMANO_LOTE, padron.size())), ahora);
        }
    }

    private void recorrer(List<UserId> lote, Instant ahora) {
        Map<UserId, ProgramaActivado> programasDelLote = programas.deVarios(lote);
        Map<UserId, Map<ClaveDelPeriodo, EstadoDeUnPeriodo>> decididosDelLote = decididosDe(programasDelLote.keySet(), ahora);
        for (Map.Entry<UserId, ProgramaActivado> entrada : programasDelLote.entrySet()) {
            generarSinTumbarElBarrido(entrada.getValue(), decididosDelLote.getOrDefault(entrada.getKey(), Map.of()), ahora);
        }
    }

    private Map<UserId, Map<ClaveDelPeriodo, EstadoDeUnPeriodo>> decididosDe(Collection<UserId> personas, Instant ahora) {
        LocalDate desde = ahora.atZone(java.time.ZoneOffset.UTC).toLocalDate().minusDays(reglas.diasDePuestaAlDia() + 70L);
        Map<UserId, Map<ClaveDelPeriodo, EstadoDeUnPeriodo>> porPersona = new HashMap<>();
        decididos.estadosDe(personas, desde).forEach(estado -> porPersona
                .computeIfAbsent(estado.participante(), persona -> new HashMap<>())
                .put(new ClaveDelPeriodo(estado.tipo(), estado.inicio()), estado));
        return porPersona;
    }

    private void generarSinTumbarElBarrido(ProgramaActivado programa, Map<ClaveDelPeriodo, EstadoDeUnPeriodo> yaDecididos, Instant ahora) {
        try {
            LocalDate hoy = ahora.atZone(programa.zona()).toLocalDate();
            for (PeriodoDelPrograma periodo : periodos.cerradosHastaHoy(programa.primeraFecha(), programa.duracionDias(), hoy, reglas.diasDePuestaAlDia())) {
                TipoDePeriodo tipo = TipoDePeriodo.valueOf(periodo.tipo().name());
                if (toca(yaDecididos.get(new ClaveDelPeriodo(tipo, periodo.inicio())), ahora)) {
                    generar.generar(programa.participanteId(), tipo, periodo.inicio());
                }
            }
        } catch (RuntimeException e) {
            log.error("[rag.BarridoDeAnalisis80_20] fallo generando para una persona: {}", e.getClass().getSimpleName(), e);
        }
    }

    /** Sin fila, o pendiente cuya espera ya pasó. Lo decidido (listo, sin datos, agotado) no se toca. */
    private boolean toca(EstadoDeUnPeriodo estado, Instant ahora) {
        if (estado == null) {
            return true;
        }
        return estado.estado() == com.renaser.os.rag.domain.model.analisis.EstadoDelAnalisis.PENDIENTE
                && (estado.ultimoIntentoEn() == null || !ahora.isBefore(estado.ultimoIntentoEn().plus(reglas.esperaEntreIntentos())));
    }

    private record ClaveDelPeriodo(TipoDePeriodo tipo, LocalDate inicio) {
    }
}
