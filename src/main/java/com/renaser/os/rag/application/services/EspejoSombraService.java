package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.espejosombra.GenerarInformeEspejoSombraUseCase;
import com.renaser.os.rag.application.ports.in.espejosombra.ListarInformesEspejoSombraUseCase;
import com.renaser.os.rag.application.ports.in.espejosombra.ObtenerInformeEspejoSombraUseCase;
import com.renaser.os.rag.application.ports.out.espejosombra.LeerEntradasDiarioPort;
import com.renaser.os.rag.application.ports.out.espejosombra.LeerEntradasDiarioPort.EntradaDiario;
import com.renaser.os.rag.application.ports.out.espejosombra.LoadInformeEspejoSombraPort;
import com.renaser.os.rag.application.ports.out.espejosombra.SaveInformeEspejoSombraPort;
import com.renaser.os.rag.application.ports.out.ia.GenerarInsightSemanalPort;
import com.renaser.os.rag.application.ports.out.ia.GenerarInsightSemanalPort.InsightSemanal;
import com.renaser.os.rag.domain.model.espejosombra.DistribucionTemporal;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombraId;
import com.renaser.os.rag.domain.model.espejosombra.PreguntaConfrontacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Único servicio de aplicación del agregado Espejo Sombra. Orquesta la generación
 * semanal (solo desde el scheduler, ver {@link GenerarInformeEspejoSombraUseCase}) y
 * las consultas con control de visibilidad (D-47).
 */
@Service
public class EspejoSombraService implements GenerarInformeEspejoSombraUseCase, ObtenerInformeEspejoSombraUseCase,
        ListarInformesEspejoSombraUseCase {

    private static final Logger log = LoggerFactory.getLogger(EspejoSombraService.class);

    /** Semana = 7 días incluyendo el de inicio (lunes a domingo). */
    private static final int DIAS_SEMANA = 7;

    private final LoadInformeEspejoSombraPort loadInformePort;
    private final SaveInformeEspejoSombraPort saveInformePort;
    private final LeerEntradasDiarioPort leerEntradasPort;
    private final GenerarInsightSemanalPort generarInsightPort;
    private final UserSummaryFinder userSummaryFinder;
    private final ParticipacionProgramaFinder participacionFinder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public EspejoSombraService(LoadInformeEspejoSombraPort loadInformePort,
                                SaveInformeEspejoSombraPort saveInformePort,
                                LeerEntradasDiarioPort leerEntradasPort,
                                GenerarInsightSemanalPort generarInsightPort,
                                UserSummaryFinder userSummaryFinder,
                                ParticipacionProgramaFinder participacionFinder,
                                Clock clock, IdGenerator idGenerator) {
        this.loadInformePort = loadInformePort;
        this.saveInformePort = saveInformePort;
        this.leerEntradasPort = leerEntradasPort;
        this.generarInsightPort = generarInsightPort;
        this.userSummaryFinder = userSummaryFinder;
        this.participacionFinder = participacionFinder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    /**
     * Tres caminos posibles, ninguno de los cuales deja el sistema en un estado
     * inconsistente:
     *
     * <ol>
     *   <li><b>Ya existe informe para esa semana</b> → no hace nada. Decisión: un
     *   informe generado es definitivo, no se regenera automáticamente. Si algún día
     *   hace falta corregir uno (ej. la IA se equivocó), eso es una operación manual
     *   de admin, fuera de este alcance — regenerar sin más pisaría silenciosamente
     *   un análisis que el aprendiz/mentor ya pudo haber leído.</li>
     *   <li><b>La semana no tiene ninguna entrada de diario con contenido</b> → no
     *   genera informe. No tiene sentido "analizar" el silencio: un informe de una
     *   semana vacía forzaría un patrón dominante inventado sobre la nada.</li>
     *   <li><b>La IA no está disponible</b> ({@link GenerarInsightSemanalPort}
     *   devuelve {@code Optional.empty()} — hoy siempre, sin credenciales, D-39) → no
     *   persiste nada y solo deja un WARN. Nunca se inventa un informe con datos
     *   falsos (CLAUDE.MD §0.6).</li>
     * </ol>
     */
    @Override
    @Transactional
    public void generar(UserId participanteId, LocalDate semanaInicio) {
        if (loadInformePort.porParticipanteYSemana(participanteId, semanaInicio).isPresent()) {
            log.debug("[rag.EspejoSombraService] informe ya existe, se omite. participante={} semana={}",
                    participanteId, semanaInicio);
            return;
        }
        List<EntradaDiario> entradas = leerEntradasPort.deLaSemana(participanteId, semanaInicio,
                semanaInicio.plusDays(DIAS_SEMANA - 1));
        if (entradas.isEmpty()) {
            log.debug("[rag.EspejoSombraService] semana sin entradas de diario, no se genera informe. "
                    + "participante={} semana={}", participanteId, semanaInicio);
            return;
        }
        Optional<InsightSemanal> insight = generarInsightPort.analizar(textosDe(entradas));
        if (insight.isEmpty()) {
            log.warn("[rag.EspejoSombraService] IA no disponible, no se genera informe. participante={} semana={}",
                    participanteId, semanaInicio);
            return;
        }
        InformeEspejoSombra informe = construirInforme(participanteId, semanaInicio, entradas.size(), insight.get());
        saveInformePort.save(informe);
        log.info("[rag.EspejoSombraService] informe generado. id={} participante={} semana={} entradas={}",
                informe.id(), participanteId, semanaInicio, entradas.size());
    }

    private static List<String> textosDe(List<EntradaDiario> entradas) {
        List<String> textos = new ArrayList<>(entradas.size());
        for (EntradaDiario entrada : entradas) {
            textos.add(entrada.texto());
        }
        return textos;
    }

    private InformeEspejoSombra construirInforme(UserId participanteId, LocalDate semanaInicio, int cantidadEntradas,
                                                    InsightSemanal insight) {
        DistribucionTemporal distribucion = new DistribucionTemporal(insight.pctPasado(), insight.pctPresente(),
                insight.pctFuturo());
        List<PreguntaConfrontacion> preguntas = aPreguntas(insight.preguntasConfrontacion());
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
        return InformeEspejoSombra.generar(InformeEspejoSombraId.of(idGenerator.newId()), participanteId,
                semanaInicio, cantidadEntradas, insight.patronDominante(), distribucion, insight.insight(),
                preguntas, clock);
    }

    /** Toma como mucho {@link InformeEspejoSombra#MAX_PREGUNTAS} preguntas de la IA, numeradas 1..N. */
    private static List<PreguntaConfrontacion> aPreguntas(List<String> preguntasIa) {
        int cantidad = Math.min(preguntasIa.size(), InformeEspejoSombra.MAX_PREGUNTAS);
        List<PreguntaConfrontacion> preguntas = new ArrayList<>(cantidad);
        for (int i = 0; i < cantidad; i++) {
            preguntas.add(new PreguntaConfrontacion(i + 1, preguntasIa.get(i)));
        }
        return preguntas;
    }

    @Override
    public InformeEspejoSombra porId(UserId actorId, InformeEspejoSombraId informeId) {
        InformeEspejoSombra informe = loadInformePort.byId(informeId)
                .orElseThrow(() -> new NoSuchElementException("Informe no encontrado: " + informeId));
        requireVisibilidad(actorId, informe.participanteId());
        return informe;
    }

    @Override
    public List<InformeEspejoSombra> deParticipante(UserId actorId, UserId participanteId) {
        requireVisibilidad(actorId, participanteId);
        return loadInformePort.deParticipante(participanteId);
    }

    /**
     * El propio aprendiz, su mentor ASIGNADO (no cualquier mentor — mismo bug que se
     * corrigió en {@code support}, E-38), ADMIN o ALCHEMIST (D-47). Se resuelve la
     * visibilidad ANTES de tocar el informe puntual para que un tercero sin relación
     * reciba siempre 403, nunca 404 — no se filtra si el informe existe.
     */
    private void requireVisibilidad(UserId actorId, UserId participanteId) {
        UserSummary actor = requireActorActivo(actorId);
        if (actor.role() == UserRole.ADMIN || actor.role() == UserRole.ALCHEMIST) {
            return;
        }
        if (actorId.equals(participanteId)) {
            return;
        }
        if (actor.role() == UserRole.MENTOR && esMentorAsignado(actorId, participanteId)) {
            return;
        }
        throw new NotAuthorizedException("No tenes visibilidad sobre los informes de ese participante");
    }

    private boolean esMentorAsignado(UserId actorId, UserId participanteId) {
        return participacionFinder.deParticipante(participanteId)
                .map(ParticipacionPrograma::mentorId)
                .map(actorId::equals)
                .orElse(false);
    }

    private UserSummary requireActorActivo(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("Cuenta suspendida");
        }
        return actor;
    }
}
