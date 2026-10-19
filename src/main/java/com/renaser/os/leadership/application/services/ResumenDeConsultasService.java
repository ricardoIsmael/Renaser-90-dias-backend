package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.leadership.api.RevisionDelLiderEvent;
import com.renaser.os.leadership.application.ports.in.ResumirConsultasDelLiderUseCase;
import com.renaser.os.leadership.application.ports.out.LeerConsultasDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas;
import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas.DiaDelMentor;
import com.renaser.os.leadership.domain.model.consulta.ReglaDelResumenDeConsultas.Resumen;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * El resumen diario «Tu líder revisó a tu grupo» (spec 005, FR-017, R-8). Cada hora mira a los mentores a quienes ahora
 * les toca —las 20:00 de SU zona— y, a los que tienen algo que contar, les publica el resumen del día. Publica, no
 * notifica: a quién llega y con qué título lo decide {@code notifications}.
 *
 * <p><b>Zona del mentor</b>: la de su programa si tiene uno; si no, la de su primer grupo; por defecto, Lima.
 *
 * <p><b>Una lectura por lote de 100 mentores</b> (misma zona y día), nunca una por mentor. <b>Cada evento sale en su
 * PROPIA transacción real</b> ({@link #transaccionPropia}) y no con un {@code @Transactional} de esta clase: la llamada
 * interna no pasaría por el proxy y el {@code @ApplicationModuleListener}, que corre después del commit, no se
 * ejecutaría nunca (E-250). Un mentor que falla no frena a los demás (regla 02 §4).
 *
 * <p><b>No pregunta si ya avisó</b>: repetir la corrida publica con la misma clave y el índice único de la bandeja
 * descarta la repetición.
 */
@Service
public class ResumenDeConsultasService implements ResumirConsultasDelLiderUseCase {

    private static final Logger log = LoggerFactory.getLogger(ResumenDeConsultasService.class);
    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of("America/Lima");
    private static final int MENTORES_POR_LOTE = 100;

    private final AcompanamientoFinder acompanamiento;
    private final ParticipacionProgramaFinder participaciones;
    private final LeerConsultasDeFichaPort consultas;
    private final UserSummaryFinder usuarios;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccionPropia;
    private final Clock clock;

    ResumenDeConsultasService(AcompanamientoFinder acompanamiento, ParticipacionProgramaFinder participaciones,
                              LeerConsultasDeFichaPort consultas, UserSummaryFinder usuarios,
                              ApplicationEventPublisher eventos, PlatformTransactionManager transactionManager,
                              Clock clock) {
        this.acompanamiento = acompanamiento;
        this.participaciones = participaciones;
        this.consultas = consultas;
        this.usuarios = usuarios;
        this.eventos = eventos;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        this.clock = clock;
    }

    @Override
    public int resumir() {
        Instant ahora = clock.now();
        int avisos = 0;
        for (List<DiaDelMentor> lote : lotes(mentoresEnSuHora(ahora))) {
            avisos += resumirLote(lote, ahora);
        }
        return avisos;
    }

    /** Los mentores con grupo vigente a quienes ahora les toca, cada uno con su día local. */
    private List<DiaDelMentor> mentoresEnSuHora(Instant ahora) {
        Map<UserId, List<GrupoAcompanado>> gruposDeCadaMentor = acompanamiento.gruposConMentorVigente(ahora).stream()
                .collect(Collectors.groupingBy(GrupoAcompanado::mentorId, LinkedHashMap::new, Collectors.toList()));
        List<DiaDelMentor> enSuHora = new ArrayList<>();
        gruposDeCadaMentor.forEach((mentor, grupos) -> {
            ZoneId zona = zonaDe(mentor, grupos.getFirst());
            if (ReglaDelResumenDeConsultas.toca(ahora, zona)) {
                enSuHora.add(DiaDelMentor.de(mentor, zona, ahora));
            }
        });
        return enSuHora;
    }

    private ZoneId zonaDe(UserId mentor, GrupoAcompanado primerGrupo) {
        Optional<ZoneId> delPrograma = participaciones.deParticipante(mentor).filter(ParticipacionPrograma::inscrito)
                .map(ParticipacionPrograma::zona);
        return delPrograma.orElseGet(() -> zonaDelGrupo(primerGrupo));
    }

    private static ZoneId zonaDelGrupo(GrupoAcompanado grupo) {
        try {
            return ZoneId.of(grupo.zonaHoraria());
        } catch (DateTimeException | NullPointerException e) {
            return ZONA_POR_DEFECTO;
        }
    }

    /** Lotes de a 100 mentores que comparten ventana: una sola lectura de consultas por lote. */
    private static List<List<DiaDelMentor>> lotes(List<DiaDelMentor> mentores) {
        Map<List<Instant>, List<DiaDelMentor>> porVentana = mentores.stream().collect(
                Collectors.groupingBy(dia -> List.of(dia.desde(), dia.hasta()), LinkedHashMap::new, Collectors.toList()));
        List<List<DiaDelMentor>> lotes = new ArrayList<>();
        porVentana.values().forEach(delMismoDia -> {
            for (int i = 0; i < delMismoDia.size(); i += MENTORES_POR_LOTE) {
                lotes.add(delMismoDia.subList(i, Math.min(i + MENTORES_POR_LOTE, delMismoDia.size())));
            }
        });
        return lotes;
    }

    private int resumirLote(List<DiaDelMentor> lote, Instant ahora) {
        List<UserId> ids = lote.stream().map(DiaDelMentor::mentorId).toList();
        Map<UserId, List<ConsultaDeFicha>> delLider = consultas.delLiderEntre(ids, lote.getFirst().desde(),
                lote.getFirst().hasta()).stream().collect(Collectors.groupingBy(ConsultaDeFicha::mentorId));
        int avisos = 0;
        for (DiaDelMentor dia : lote) {
            if (avisarAislado(dia, delLider.getOrDefault(dia.mentorId(), List.of()), ahora)) {
                avisos++;
            }
        }
        return avisos;
    }

    private boolean avisarAislado(DiaDelMentor dia, List<ConsultaDeFicha> delMentor, Instant ahora) {
        try {
            return avisar(dia, delMentor, ahora);
        } catch (RuntimeException e) {
            log.error("[leadership.ResumenDeConsultasService] fallo el resumen del mentor {}; sigue el barrido",
                    dia.mentorId(), e);
            return false;
        }
    }

    private boolean avisar(DiaDelMentor dia, List<ConsultaDeFicha> delMentor, Instant ahora) {
        Optional<Resumen> resumen = ReglaDelResumenDeConsultas.resumir(dia, delMentor);
        if (resumen.isEmpty()) {
            return false;
        }
        List<String> nombres = nombresDe(resumen.get().personas());
        if (nombres.isEmpty()) {
            return false;
        }
        RevisionDelLiderEvent evento = new RevisionDelLiderEvent(dia.clave(), dia.mentorId().value(),
                resumen.get().grupoDeLaRuta(), ReglaDelResumenDeConsultas.cuerpo(nombres), nombres.size(), dia.dia(),
                ahora);
        transaccionPropia.executeWithoutResult(estado -> eventos.publishEvent(evento));
        return true;
    }

    /** Los primeros nombres, en el orden en que se miraron; una cuenta que ya no existe no se nombra. */
    private List<String> nombresDe(List<UserId> personas) {
        Map<UserId, UserSummary> cuentas = usuarios.findByIds(personas);
        return personas.stream().filter(cuentas::containsKey)
                .map(persona -> ReglaDelResumenDeConsultas.primerNombre(cuentas.get(persona).fullName())).toList();
    }
}
