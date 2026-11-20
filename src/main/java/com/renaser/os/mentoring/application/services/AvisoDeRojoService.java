package com.renaser.os.mentoring.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AcompanamientoFinder.GrupoAcompanado;
import com.renaser.os.community.api.ZonaDelAcompananteFinder;
import com.renaser.os.mentoring.api.RojoDelDiaEvent;
import com.renaser.os.mentoring.application.ports.in.AvisarRojosDelDiaUseCase;
import com.renaser.os.mentoring.domain.model.aviso.ReglaDelAvisoDeRojo;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.DiaDelSemaforo;
import com.renaser.os.points.api.EstadoDiaSemaforo;
import com.renaser.os.points.api.SemaforoDelDiaFinder;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * El aviso de rojo del mismo día (spec 057, FR-031 a FR-033). Cada 30 minutos mira a los mentores a quienes ahora les toca —las 19:00 de SU
 * zona— y, de los aprendices vigentes de sus grupos, publica un evento por cada uno que hoy está en rojo según el semáforo del día. Publica,
 * no notifica: a quién llega y con qué texto lo decide {@code notifications}.
 *
 * <p>Todo se reutiliza: el padrón de cada grupo es el de {@link MedicionDeGrupos} (solo cuentas activas, sin el propio mentor), el color del
 * día sale de {@link SemaforoDelDiaFinder} (solo cuenta un día {@code MEDIDO}: sin datos, pausado, suspendido o fuera del programa no avisa) y
 * la zona del mentor sale de {@link ZonaDelAcompananteFinder}, el mismo del resumen del líder.
 *
 * <p>El día es el LOCAL DEL APRENDIZ: las 19:00 del mentor pueden caer en otro día del aprendiz. Cada mentor sale en su PROPIA transacción real
 * ({@link TransactionTemplate}), no con un {@code @Transactional} de esta clase: la llamada interna no pasaría por el proxy y el
 * {@code @ApplicationModuleListener}, que corre después del commit, no se ejecutaría nunca (E-250). Un mentor que falla no frena a los demás.
 */
@Service
class AvisoDeRojoService implements AvisarRojosDelDiaUseCase {

    private static final Logger log = LoggerFactory.getLogger(AvisoDeRojoService.class);

    private final AcompanamientoFinder acompanamiento;
    private final ParticipacionProgramaFinder participaciones;
    private final ZonaDelAcompananteFinder zonas;
    private final MedicionDeGrupos medicion;
    private final SemaforoDelDiaFinder semaforoDelDia;
    private final ApplicationEventPublisher eventos;
    private final TransactionTemplate transaccionPropia;
    private final Clock clock;

    AvisoDeRojoService(AcompanamientoFinder acompanamiento, ParticipacionProgramaFinder participaciones, ZonaDelAcompananteFinder zonas,
                       MedicionDeGrupos medicion,
                       SemaforoDelDiaFinder semaforoDelDia, ApplicationEventPublisher eventos,
                       PlatformTransactionManager transactionManager, Clock clock) {
        this.acompanamiento = acompanamiento;
        this.participaciones = participaciones;
        this.zonas = zonas;
        this.medicion = medicion;
        this.semaforoDelDia = semaforoDelDia;
        this.eventos = eventos;
        this.transaccionPropia = new TransactionTemplate(transactionManager);
        this.transaccionPropia.setPropagationBehavior(Propagation.REQUIRES_NEW.value());
        this.clock = clock;
    }

    @Override
    public int avisar() {
        Instant ahora = clock.now();
        Map<UserId, List<GrupoAcompanado>> gruposDeCadaMentor = acompanamiento.gruposConMentorVigente(ahora).stream()
                .collect(Collectors.groupingBy(GrupoAcompanado::mentorId, LinkedHashMap::new, Collectors.toList()));
        int publicados = 0;
        for (Map.Entry<UserId, List<GrupoAcompanado>> delMentor : gruposDeCadaMentor.entrySet()) {
            if (!ReglaDelAvisoDeRojo.toca(ahora, zonas.zonaDe(delMentor.getKey(), ahora))) {
                continue;
            }
            try {
                Integer avisos = transaccionPropia.execute(estado -> avisarAlMentor(delMentor.getKey(), delMentor.getValue(), ahora));
                publicados += avisos == null ? 0 : avisos;
            } catch (RuntimeException e) {
                log.error("[mentoring.AvisoDeRojoService] fallo el mentor {}; sigue el barrido", delMentor.getKey(), e);
            }
        }
        return publicados;
    }

    /** Los aprendices de todos sus grupos vigentes, cada uno en su día local, y un evento por cada rojo medido. */
    private int avisarAlMentor(UserId mentor, List<GrupoAcompanado> grupos, Instant ahora) {
        Map<UserId, UUID> grupoDe = new LinkedHashMap<>();
        Map<UserId, UserSummary> perfiles = new LinkedHashMap<>();
        for (GrupoAcompanado grupo : grupos) {
            medicion.padronDe(grupo.grupoId(), ahora).forEach((aprendiz, perfil) -> {
                grupoDe.putIfAbsent(aprendiz, grupo.grupoId());
                perfiles.putIfAbsent(aprendiz, perfil);
            });
        }
        int publicados = 0;
        for (Map.Entry<LocalDate, List<UserId>> delDia : aprendicesPorDiaLocal(grupoDe.keySet(), ahora).entrySet()) {
            for (Map.Entry<UserId, DiaDelSemaforo> rojo : enRojo(delDia.getValue(), delDia.getKey()).entrySet()) {
                UserId aprendiz = rojo.getKey();
                eventos.publishEvent(new RojoDelDiaEvent(
                        ReglaDelAvisoDeRojo.clave(mentor.value(), aprendiz.value(), ReglaDelAvisoDeRojo.lunesDe(delDia.getKey())),
                        mentor.value(), aprendiz.value(), grupoDe.get(aprendiz), nombreDe(perfiles.get(aprendiz)),
                        rojo.getValue().porcentaje(), ahora));
                publicados++;
            }
        }
        return publicados;
    }

    /** Agrupa por día local del aprendiz para hacer UNA lectura del semáforo por día, no una por persona. Sin programa activado no se mide. */
    private Map<LocalDate, List<UserId>> aprendicesPorDiaLocal(Iterable<UserId> aprendices, Instant ahora) {
        List<UserId> todos = new ArrayList<>();
        aprendices.forEach(todos::add);
        Map<LocalDate, List<UserId>> porDia = new LinkedHashMap<>();
        participaciones.deParticipantes(todos).forEach((aprendiz, participacion) ->
                porDia.computeIfAbsent(ahora.atZone(participacion.zona()).toLocalDate(), dia -> new ArrayList<>()).add(aprendiz));
        return porDia;
    }

    private Map<UserId, DiaDelSemaforo> enRojo(List<UserId> aprendices, LocalDate dia) {
        Map<UserId, DiaDelSemaforo> rojos = new LinkedHashMap<>();
        semaforoDelDia.delDia(aprendices, dia).forEach((aprendiz, delDia) -> {
            if (delDia.estado() == EstadoDiaSemaforo.MEDIDO && delDia.color() == ColorSemaforo.ROJO) {
                rojos.put(aprendiz, delDia);
            }
        });
        return rojos;
    }

    private static String nombreDe(UserSummary perfil) {
        return perfil != null && perfil.fullName() != null && !perfil.fullName().isBlank() ? perfil.fullName() : "Un aprendiz de tu grupo";
    }
}
