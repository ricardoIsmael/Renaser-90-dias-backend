package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.mentoring.api.ResumenSemanalDelGrupoEvent;
import com.renaser.os.mentoring.api.ResumenSemanalGeneralEvent;
import com.renaser.os.mentoring.application.ports.in.ResumirSemanaDelSemaforoUseCase;
import com.renaser.os.mentoring.domain.model.semaforo.ConteoPorColor;
import com.renaser.os.mentoring.domain.model.resumen.ReglasDelResumenSemanal;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.points.api.SemanaDelSemaforo;
import com.renaser.os.points.api.VentanaDelSemaforo;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El resumen del sábado del semáforo (D-168): por cada grupo que acaba de cerrar su semana, cuántos
 * de sus aprendices quedaron en cada color, para su mentor; y la suma de todos, para el líder, admin
 * y alquimista. Publica, no notifica: quién lo recibe y con qué texto lo decide {@code notifications}.
 *
 * <p><b>Cada evento sale en su PROPIA transacción real</b> ({@link #transaccionPropia}), no con un
 * {@code @Transactional} sobre un método de esta misma clase: una llamada interna no pasa por el
 * proxy de Spring, el evento saldría sin transacción y el {@code @ApplicationModuleListener} —que
 * corre después del commit— no se ejecutaría nunca (E-250). Una por evento y no una por barrido: un
 * grupo que falla no arrastra a los demás (regla 02 §4).
 *
 * <p><b>No pregunta si ya avisó.</b> Dentro de la ventana cada corrida vuelve a publicar con la misma
 * clave y el índice único de {@code notificaciones} descarta la repetición (como {@code AvisosService}).
 *
 * <p>El resumen general suma los grupos publicados en ESTA corrida, que son todos de la misma semana
 * (dos zonas solo coinciden en la ventana en la madrugada del mismo sábado). Con todos los grupos en
 * una zona (hoy, America/Lima) entran todos; con zonas distintas, la primera en cerrar se lleva la
 * clave de la semana y las demás no entran en la suma.
 */
@Service
public class ResumenSemanalDelSemaforoService implements ResumirSemanaDelSemaforoUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResumenSemanalDelSemaforoService.class);

    private final AcompanamientoFinder acompanamientoFinder;
    private final SemaforoFinder semaforoFinder;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccionPropia;
    private final Clock clock;

    public ResumenSemanalDelSemaforoService(AcompanamientoFinder acompanamientoFinder, SemaforoFinder semaforoFinder,
                                            ApplicationEventPublisher eventos,
                                            PlatformTransactionManager transactionManager, Clock clock) {
        this.acompanamientoFinder = acompanamientoFinder;
        this.semaforoFinder = semaforoFinder;
        this.eventos = eventos;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        this.clock = clock;
    }

    @Override
    public int resumir() {
        Instant ahora = clock.now();
        List<ResumenDeGrupo> publicados = new ArrayList<>();
        for (GrupoAcompanado grupo : acompanamientoFinder.gruposConMentorVigente(ahora)) {
            resumirAislado(grupo, ahora).ifPresent(publicados::add);
        }
        if (!publicados.isEmpty()) {
            publicarGeneral(publicados, ahora);
        }
        return publicados.size();
    }

    private Optional<ResumenDeGrupo> resumirAislado(GrupoAcompanado grupo, Instant ahora) {
        try {
            return resumirGrupo(grupo, ahora);
        } catch (RuntimeException e) {
            log.error("[mentoring.ResumenSemanalDelSemaforoService] fallo el resumen del grupo {}; sigue el barrido",
                    grupo.grupoId(), e);
            return Optional.empty();
        }
    }

    private Optional<ResumenDeGrupo> resumirGrupo(GrupoAcompanado grupo, Instant ahora) {
        Optional<LocalDate> semana = ReglasDelResumenSemanal.semanaQueToca(ahora, ZoneId.of(grupo.zonaHoraria()));
        if (semana.isEmpty()) {
            return Optional.empty();
        }
        List<UserId> aprendices = aprendicesSinElMentor(grupo, ahora);
        Map<UserId, VentanaDelSemaforo> semanas = semanasDe(aprendices, semana.get());
        if (!ReglasDelResumenSemanal.semaforoListo(aprendices, semanas)) {
            return Optional.empty();
        }
        ResumenDeGrupo resumen = new ResumenDeGrupo(semana.get(), ReglasDelResumenSemanal.conteoDelCierre(aprendices, semanas));
        publicar(eventoDelGrupo(grupo, resumen, ahora));
        return Optional.of(resumen);
    }

    /** El mentor que además cursa no entra en el semáforo de su propio grupo (§4.3 del contrato). */
    private List<UserId> aprendicesSinElMentor(GrupoAcompanado grupo, Instant ahora) {
        return acompanamientoFinder.aprendicesVigentes(grupo.grupoId(), ahora).stream()
                .filter(aprendiz -> !aprendiz.equals(grupo.mentorId()))
                .toList();
    }

    /** Una sola consulta para todo el grupo, nunca una por aprendiz. */
    private Map<UserId, VentanaDelSemaforo> semanasDe(List<UserId> aprendices, LocalDate semanaHasta) {
        return aprendices.isEmpty() ? Map.of() : semaforoFinder.semanaDe(aprendices, semanaHasta);
    }

    /** Si falla, la corrida siguiente de la ventana lo vuelve a intentar con la misma clave. */
    private void publicarGeneral(List<ResumenDeGrupo> publicados, Instant ahora) {
        ResumenSemanalGeneralEvent general = eventoGeneral(publicados, ahora);
        try {
            publicar(general);
        } catch (RuntimeException e) {
            log.error("[mentoring.ResumenSemanalDelSemaforoService] fallo el resumen general de la semana {}",
                    general.hasta(), e);
        }
    }

    private void publicar(Object evento) {
        transaccionPropia.executeWithoutResult(estado -> eventos.publishEvent(evento));
    }

    private static ResumenSemanalDelGrupoEvent eventoDelGrupo(GrupoAcompanado grupo, ResumenDeGrupo resumen,
                                                              Instant ahora) {
        LocalDate hasta = resumen.semanaHasta();
        ConteoPorColor conteo = resumen.conteo();
        return new ResumenSemanalDelGrupoEvent(ReglasDelResumenSemanal.claveDelGrupo(grupo.grupoId(), hasta),
                grupo.mentorId().value(), grupo.grupoId(), grupo.nombre(), SemanaDelSemaforo.desde(hasta), hasta,
                conteo.verde(), conteo.amarillo(), conteo.rojo(), conteo.sinDatos(), conteo.total(), ahora);
    }

    private static ResumenSemanalGeneralEvent eventoGeneral(List<ResumenDeGrupo> grupos, Instant ahora) {
        LocalDate hasta = grupos.getFirst().semanaHasta();
        ConteoPorColor suma = grupos.stream().map(ResumenDeGrupo::conteo)
                .reduce(ConteoPorColor.NINGUNO, ConteoPorColor::mas);
        return new ResumenSemanalGeneralEvent(ReglasDelResumenSemanal.claveGeneral(hasta),
                SemanaDelSemaforo.desde(hasta), hasta, grupos.size(), suma.verde(), suma.amarillo(), suma.rojo(),
                suma.sinDatos(), suma.total(), ahora);
    }

    /** Lo que el resumen general necesita de cada grupo que se publicó. */
    private record ResumenDeGrupo(LocalDate semanaHasta, ConteoPorColor conteo) {
    }
}
