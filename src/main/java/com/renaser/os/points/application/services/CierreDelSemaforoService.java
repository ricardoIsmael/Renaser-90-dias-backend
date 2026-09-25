package com.renaser.os.points.application.services;

import com.renaser.os.points.api.ConteoDelDia;
import com.renaser.os.points.api.ConteoDiarioHabitosFinder;
import com.renaser.os.points.api.ConteoDiarioObjetivosFinder;
import com.renaser.os.points.application.ports.in.semaforo.CerrarSemaforoUseCase;
import com.renaser.os.points.application.ports.out.semaforo.PausasDelSemaforoPort;
import com.renaser.os.points.application.ports.out.semaforo.SemanasDelSemaforoPort;
import com.renaser.os.points.application.services.CierreDeParticipanteService.ConteosDelRango;
import com.renaser.os.points.application.services.CierreDeParticipanteService.PersonaPorCerrar;
import com.renaser.os.points.application.services.CierreDeParticipanteService.ResultadoDeParticipante;
import com.renaser.os.points.domain.model.semaforo.CalendarioDeMedicion;
import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.points.domain.model.semaforo.PlanDeCierre;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import com.renaser.os.users.api.ProgramasActivadosFinder.ProgramaActivado;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El barrido horario del semáforo (D-168). Por página de programas activados: una consulta de
 * hábitos y una de objetivos para TODOS (las de D-43), más las de sus propias tablas. Nada se
 * calcula al subir un hábito: esto es lo único que calcula.
 *
 * <p><b>Sin {@code @Transactional} sobre el barrido</b> (regla 02 §4): cada persona se guarda en su
 * propia transacción ({@link CierreDeParticipanteService}) y una que falla no frena a las demás. Como
 * todo es derivado de fechas, lo que falló se pone al día solo en la próxima corrida.
 */
@Service
public class CierreDelSemaforoService implements CerrarSemaforoUseCase {

    private static final Logger log = LoggerFactory.getLogger(CierreDelSemaforoService.class);

    /** Mismo tamaño de página que el reloj del programa ({@code RelojProgramaService}). */
    static final int TAMANO_LOTE = 500;

    private final ProgramasActivadosFinder programasFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final SemanasDelSemaforoPort semanasPort;
    private final PausasDelSemaforoPort pausasPort;
    private final ConteosEnLote conteos;
    private final CierreDeParticipanteService cierreDeParticipante;
    private final Clock clock;

    public CierreDelSemaforoService(ProgramasActivadosFinder programasFinder, UserSummaryFinder userSummaryFinder,
                                    SemanasDelSemaforoPort semanasPort, PausasDelSemaforoPort pausasPort,
                                    ConteoDiarioHabitosFinder habitosFinder, ConteoDiarioObjetivosFinder objetivosFinder,
                                    CierreDeParticipanteService cierreDeParticipante, Clock clock) {
        this.programasFinder = programasFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.semanasPort = semanasPort;
        this.pausasPort = pausasPort;
        this.conteos = new ConteosEnLote(habitosFinder, objetivosFinder);
        this.cierreDeParticipante = cierreDeParticipante;
        this.clock = clock;
    }

    @Override
    public ResultadoDelCierre cerrarPendientes() {
        Instant ahora = clock.now();
        ResultadoDelCierre total = ResultadoDelCierre.VACIO;
        int offset = 0;
        List<ProgramaActivado> pagina;
        do {
            pagina = programasFinder.pagina(offset, TAMANO_LOTE);
            total = total.mas(procesar(pagina, ahora));
            offset += TAMANO_LOTE;
        } while (pagina.size() == TAMANO_LOTE);
        return total;
    }

    private ResultadoDelCierre procesar(List<ProgramaActivado> pagina, Instant ahora) {
        List<ProgramaActivado> medibles = medibles(pagina);
        if (medibles.isEmpty()) {
            return ResultadoDelCierre.VACIO;
        }
        List<UserId> ids = medibles.stream().map(ProgramaActivado::participanteId).toList();
        Planificador planificador = new Planificador(semanasPort.ultimaCerradaDe(ids), pausasPort.de(ids), ahora);
        List<PersonaPorCerrar> conTrabajo = medibles.stream()
                .map(planificador::planear)
                .filter(persona -> persona.plan().tieneTrabajo())
                .toList();
        return ejecutar(conTrabajo, medibles.size(), ahora);
    }

    /** Programas con fechas coherentes de cuentas ACTIVAS: un suspendido no se mide mientras lo esté. */
    private List<ProgramaActivado> medibles(List<ProgramaActivado> pagina) {
        List<ProgramaActivado> conFechas = pagina.stream()
                .filter(p -> p.primeraFecha() != null && p.ultimaFecha() != null)
                .toList();
        if (conFechas.isEmpty()) {
            return List.of();
        }
        Map<UserId, UserSummary> usuarios = userSummaryFinder.findByIds(
                conFechas.stream().map(ProgramaActivado::participanteId).toList());
        return conFechas.stream()
                .filter(p -> usuarios.containsKey(p.participanteId())
                        && usuarios.get(p.participanteId()).status() == UserStatus.ACTIVE)
                .toList();
    }


    private ResultadoDelCierre ejecutar(List<PersonaPorCerrar> personas, int evaluados, Instant ahora) {
        Map<UserId, ConteosDelRango> delRango = conteos.para(personas);
        int dias = 0;
        int semanas = 0;
        int fallidos = 0;
        for (PersonaPorCerrar persona : personas) {
            try {
                ResultadoDeParticipante suyo = cierreDeParticipante.cerrar(persona,
                        delRango.getOrDefault(persona.participanteId(), ConteosEnLote.NADA), ahora);
                dias += suyo.diasGuardados();
                semanas += suyo.semanasCerradas();
            } catch (RuntimeException e) {
                // Una persona que falla no frena el barrido (regla 02 §4); se reintenta sola.
                fallidos++;
                log.error("[points.CierreDelSemaforo] fallo el participante {}; sigue el barrido",
                        persona.participanteId(), e);
            }
        }
        return new ResultadoDelCierre(evaluados, dias, semanas, fallidos);
    }

    /** Decide, con lo ya leído de la página, qué hacer con cada persona en SU hora local. */
    private record Planificador(Map<UserId, LocalDate> ultimasCerradas, Map<UserId, List<PausaDeMedicion>> pausas,
                                Instant ahora) {

        PersonaPorCerrar planear(ProgramaActivado programa) {
            UserId id = programa.participanteId();
            CalendarioDeMedicion calendario =
                    new CalendarioDeMedicion(programa.primeraFecha(), programa.ultimaFecha(), pausas.get(id));
            LocalDate hoyLocal = ahora.atZone(programa.zona()).toLocalDate();
            return new PersonaPorCerrar(id, PlanDeCierre.para(calendario, hoyLocal, ultimasCerradas.get(id)));
        }
    }

    /** Las dos consultas en lote de la página: hábitos y objetivos, del rango que cubre a todos. */
    private record ConteosEnLote(ConteoDiarioHabitosFinder habitosFinder, ConteoDiarioObjetivosFinder objetivosFinder) {

        static final ConteosDelRango NADA = new ConteosDelRango(List.of(), List.of());

        Map<UserId, ConteosDelRango> para(List<PersonaPorCerrar> personas) {
            List<PersonaPorCerrar> conDias = personas.stream().filter(p -> p.plan().hayDiasPorCalcular()).toList();
            if (conDias.isEmpty()) {
                return Map.of();
            }
            List<UserId> ids = conDias.stream().map(PersonaPorCerrar::participanteId).toList();
            LocalDate desde = conDias.stream().map(p -> p.plan().desde()).min(LocalDate::compareTo).orElseThrow();
            LocalDate hasta = conDias.stream().map(p -> p.plan().hasta()).max(LocalDate::compareTo).orElseThrow();
            Map<UserId, List<ConteoDelDia>> habitos = habitosFinder.porParticipanteEntre(ids, desde, hasta);
            Map<UserId, List<ConteoDelDia>> objetivos = objetivosFinder.porParticipanteEntre(ids, desde, hasta);
            Map<UserId, ConteosDelRango> resultado = new HashMap<>();
            ids.forEach(id -> resultado.put(id, new ConteosDelRango(habitos.getOrDefault(id, List.of()),
                    objetivos.getOrDefault(id, List.of()))));
            return resultado;
        }
    }
}
