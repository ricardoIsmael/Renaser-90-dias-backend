package com.renaser.os.community.application.services;

import com.renaser.os.community.api.ComposicionDeCelulaCambiadaEvent;
import com.renaser.os.community.application.ports.in.acompanamiento.RotarMentoresUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.acompanamiento.SaveAsignacionPort;
import com.renaser.os.community.application.ports.out.celula.ExistePerfilMentorPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.celula.SaveCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionId;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.MotivoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeRotacion;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeRotacion.CambioDeMentor;
import com.renaser.os.community.domain.model.acompanamiento.PlanificadorDeRotacion.PlanDeRotacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.cohorte.EstadoCohorte;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AsignacionCelulaPort;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Aplica la rotación de mentores de una cohorte.
 *
 * <p>El orden de los pasos no es estético, es lo único que hace posible un intercambio A↔B:
 * tanto {@code celulas.mentor_id} como el intervalo abierto de MENTOR son UNIQUE, así que
 * <b>primero se cierran y se vacían todas las referencias salientes y se hace flush, y recién
 * después se abren las entrantes</b>. Al revés, la primera inserción choca contra la fila que
 * todavía no se liberó (plan.md §5).
 *
 * <p>Todo ocurre en una transacción por cohorte. Si algo falla a mitad, el rollback deja la
 * situación anterior completa: nunca un grupo sin mentor y otro con dos.
 */
@Service
public class RotacionService implements RotarMentoresUseCase {

    private static final Logger log = LoggerFactory.getLogger(RotacionService.class);

    private final LoadCelulaPort loadCelulaPort;
    private final SaveCelulaPort saveCelulaPort;
    private final LoadCohortePort loadCohortePort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final SaveAsignacionPort saveAsignacionPort;
    private final LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;
    private final ExistePerfilMentorPort existePerfilMentorPort;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final AsignacionCelulaPort asignacionCelulaPort;
    private final ApplicationEventPublisher eventos;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public RotacionService(LoadCelulaPort loadCelulaPort, SaveCelulaPort saveCelulaPort,
                            LoadCohortePort loadCohortePort, LoadAsignacionesPort loadAsignacionesPort,
                            SaveAsignacionPort saveAsignacionPort,
                            LoadPoliticaMentoriaPort loadPoliticaMentoriaPort,
                            ExistePerfilMentorPort existePerfilMentorPort,
                            ParticipacionProgramaFinder participacionProgramaFinder,
                            AsignacionCelulaPort asignacionCelulaPort, ApplicationEventPublisher eventos,
                            Clock clock, IdGenerator idGenerator) {
        this.loadCelulaPort = loadCelulaPort;
        this.saveCelulaPort = saveCelulaPort;
        this.loadCohortePort = loadCohortePort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.saveAsignacionPort = saveAsignacionPort;
        this.loadPoliticaMentoriaPort = loadPoliticaMentoriaPort;
        this.existePerfilMentorPort = existePerfilMentorPort;
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.asignacionCelulaPort = asignacionCelulaPort;
        this.eventos = eventos;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * Una transacción por cohorte, no una por lote. Aislar el fallo de una cohorte permite que
     * las demás se procesen igual (plan.md §11, V26).
     */
    @Override
    public List<ResultadoRotacion> rotarLasQueCorresponda() {
        List<ResultadoRotacion> resultados = new ArrayList<>();
        for (Cohorte cohorte : loadCohortePort.listar(EstadoCohorte.ACTIVA)) {
            PoliticaMentoria politica = politicaDe(cohorte.id());
            LocalDate hoy = politica.fechaLocalDe(clock.now());
            if (!esDiaDeAnclaje(politica, hoy)) {
                continue;
            }
            try {
                resultados.add(rotar(cohorte.id(), claveDelPeriodo(cohorte.id(), politica, hoy)));
            } catch (RuntimeException e) {
                log.error("[community.RotacionService] la cohorte {} fallo; se siguen procesando las demas",
                        cohorte.id(), e);
            }
        }
        return resultados;
    }

    @Override
    @Transactional
    public ResultadoRotacion rotar(CohorteId cohorteId, String claveOperacion) {
        Instant ahora = clock.now();

        List<Celula> gruposRegulares = loadCelulaPort.porCohorte(cohorteId).stream()
                .filter(c -> c.tipo() == TipoCelula.REGULAR)
                .toList();

        Map<CelulaId, ConjuntoAsignaciones> composiciones = new LinkedHashMap<>();
        Map<CelulaId, UserId> mentorPorGrupo = new HashMap<>();
        for (Celula grupo : gruposRegulares) {
            ConjuntoAsignaciones composicion = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(grupo.id()));
            composiciones.put(grupo.id(), composicion);
            composicion.mentorVigenteEn(grupo.id(), ahora).ifPresent(m -> mentorPorGrupo.put(grupo.id(), m));
        }

        PlanDeRotacion plan = PlanificadorDeRotacion.planificar(
                gruposRegulares.stream().map(Celula::id).toList(),
                mentorPorGrupo,
                mentoresLibres(mentorPorGrupo.values()),
                claveOperacion);

        return aplicar(cohorteId, plan, gruposRegulares, composiciones, ahora);
    }

    private ResultadoRotacion aplicar(CohorteId cohorteId, PlanDeRotacion plan, List<Celula> grupos,
                                       Map<CelulaId, ConjuntoAsignaciones> composiciones, Instant ahora) {
        Map<CelulaId, Celula> porId = new HashMap<>();
        grupos.forEach(g -> porId.put(g.id(), g));

        List<CambioDeMentor> pendientes = new ArrayList<>();
        int yaAplicados = 0;
        for (CambioDeMentor cambio : plan.cambios()) {
            if (loadAsignacionesPort.porClaveOperacion(cambio.claveOperacion()).isPresent()) {
                // Corrida anterior que ya hizo este paso. Repetir el job no abre otro intervalo.
                yaAplicados++;
                continue;
            }
            pendientes.add(cambio);
        }

        // PASO 1 — liberar. Se cierran los intervalos salientes y se vacia celulas.mentor_id de
        // TODOS los grupos afectados antes de tocar uno solo de los entrantes. saveAndFlush
        // empuja el UPDATE ahora, no al cerrar la sesion.
        for (CambioDeMentor cambio : pendientes) {
            composiciones.get(cambio.grupo()).todas().stream()
                    .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR)
                    .filter(a -> a.vigenteEn(ahora))
                    .forEach(saliente -> {
                        saliente.cerrar(ahora, MotivoAsignacion.ROTACION);
                        saveAsignacionPort.save(saliente);
                    });
            Celula grupo = porId.get(cambio.grupo());
            grupo.quitarMentor(ahora);
            saveCelulaPort.save(grupo);
        }

        // PASO 2 — asignar. Recien ahora las referencias UNIQUE estan libres.
        for (CambioDeMentor cambio : pendientes) {
            Celula grupo = porId.get(cambio.grupo());
            grupo.asignarMentor(cambio.mentorEntrante(), ahora);
            saveCelulaPort.save(grupo);

            saveAsignacionPort.save(AsignacionCelula.abrir(AsignacionId.of(idGenerator.newId()), cambio.grupo(),
                    cambio.mentorEntrante(), FuncionAcompanamiento.MENTOR, ahora, MotivoAsignacion.ROTACION,
                    null, cambio.claveOperacion()));

            sincronizarPunterosDeAlumnos(cambio.grupo(), composiciones.get(cambio.grupo()), cambio.mentorEntrante(),
                    ahora);

            // Dentro de la MISMA transaccion: si la rotacion se deshace, el aviso tampoco sale.
            // El outbox de Modulith lo entrega despues del commit (plan.md §5).
            eventos.publishEvent(new ComposicionDeCelulaCambiadaEvent(cambio.grupo().value(), ahora));
        }

        if (!plan.gruposSinSustituto().isEmpty() || !plan.gruposSinCobertura().isEmpty()) {
            log.warn("[community.RotacionService] cohorte {}: {} grupo(s) sin sustituto, {} sin cobertura",
                    cohorteId, plan.gruposSinSustituto().size(), plan.gruposSinCobertura().size());
        }

        return new ResultadoRotacion(cohorteId.value(), plan.claveOperacion(), pendientes.size(), yaAplicados,
                plan.gruposSinSustituto().stream().map(c -> c.value()).toList(),
                plan.gruposSinCobertura().stream().map(c -> c.value()).toList());
    }

    /**
     * El puntero {@code participantes_programa.mentor_id} es el que decide a quién se le
     * autoriza la evidencia del aprendiz. Si la rotación no lo mueve, el mentor saliente sigue
     * autorizado sobre gente que ya no acompaña (research.md).
     */
    private void sincronizarPunterosDeAlumnos(CelulaId grupoId, ConjuntoAsignaciones composicion,
                                               UserId mentorEntrante, Instant ahora) {
        for (UserId aprendiz : composicion.aprendicesVigentesEn(grupoId, ahora)) {
            asignacionCelulaPort.sincronizarAcompanamiento(aprendiz, grupoId.value(), mentorEntrante);
        }
    }

    /** Mentores activos, con perfil de mentor y sin grupo regular vigente. */
    private List<UserId> mentoresLibres(java.util.Collection<UserId> yaAsignados) {
        Set<UserId> ocupados = Set.copyOf(yaAsignados);
        return participacionProgramaFinder.usuariosActivosConRol(Set.of(UserRole.MENTOR)).stream()
                .filter(m -> !ocupados.contains(m))
                .filter(existePerfilMentorPort::existe)
                .toList();
    }

    /**
     * Hoy es día de anclaje si la fecha anterior "apunta" a hoy. Se compara contra el calendario
     * y no contra "pasaron 30 días", para que todos los grupos de la cohorte roten el mismo día
     * y el mes evaluado coincida con el que el mentor ve en pantalla (P-02).
     */
    private static boolean esDiaDeAnclaje(PoliticaMentoria politica, LocalDate hoy) {
        return politica.cadenciaRotacion().proximaFechaDespuesDe(hoy.minusDays(1)).equals(hoy);
    }

    /**
     * Estable por cohorte y período: si el job se cayó y vuelve mañana, recalcula la misma clave
     * y los pasos ya hechos se saltean en vez de duplicarse. El período es la fecha de anclaje,
     * no la de ejecución — así una corrida atrasada aplica LA transición que faltaba, una sola
     * vez, sin fabricar las de los meses perdidos (plan.md §5).
     */
    private static String claveDelPeriodo(CohorteId cohorteId, PoliticaMentoria politica, LocalDate hoy) {
        return "rotacion:" + cohorteId.value() + ":" + politica.cadenciaRotacion() + ":" + hoy;
    }

    private PoliticaMentoria politicaDe(CohorteId cohorteId) {
        return loadPoliticaMentoriaPort.porCohorte(cohorteId)
                .orElseGet(() -> PoliticaMentoria.porDefecto(cohorteId));
    }
}
