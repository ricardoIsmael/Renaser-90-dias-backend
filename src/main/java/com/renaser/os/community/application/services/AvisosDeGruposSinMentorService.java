package com.renaser.os.community.application.services;

import com.renaser.os.community.api.FaltaArmarGrupoEvent;
import com.renaser.os.community.application.ports.in.celula.DetectarGruposSinMentorUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.celula.AvisoDeArmadoDeGrupos;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Avisa al líder de mentores de cada grupo EN CURSO que no tiene mentor (D-240, E-478).
 *
 * <p>Hasta el 2026-10-01 un grupo así desaparecía del semáforo del líder (que solo listaba grupos
 * con mentor vigente) y nadie se enteraba. Ahora aparece marcado «sin mentor» y además llega este
 * aviso, uno por grupo y por día local (la clave de {@link AvisoDeArmadoDeGrupos}).
 *
 * <p>«En curso» y «tiene mentor» salen de las mismas fuentes que el acceso: {@link VigenciaDeGrupos}
 * y la asignación de MENTOR vigente, no {@code celulas.mentor_id}.
 */
@Service
public class AvisosDeGruposSinMentorService implements DetectarGruposSinMentorUseCase {

    private static final Logger log = LoggerFactory.getLogger(AvisosDeGruposSinMentorService.class);

    private final LoadCelulaPort loadCelulaPort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final VigenciaDeGrupos vigencia;

    public AvisosDeGruposSinMentorService(LoadCelulaPort loadCelulaPort, LoadAsignacionesPort loadAsignacionesPort,
                                           LoadPoliticaMentoriaPort loadPoliticaMentoriaPort,
                                           ApplicationEventPublisher publisher, Clock clock) {
        this.loadCelulaPort = loadCelulaPort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.publisher = publisher;
        this.clock = clock;
        this.vigencia = new VigenciaDeGrupos(loadPoliticaMentoriaPort);
    }

    /**
     * Los grupos son decenas y aca solo se lee y se publica: una transaccion para todo el barrido,
     * igual que {@code AvisosDeVencimientoService}. El outbox de Modulith entrega los avisos despues
     * del commit.
     */
    @Override
    @Transactional
    public int avisarDeLosQueNoTienenMentor() {
        Instant ahora = clock.now();
        int publicados = 0;
        for (Celula celula : loadCelulaPort.todas()) {
            if (!tocaAvisar(celula, ahora)) {
                continue;
            }
            publisher.publishEvent(new FaltaArmarGrupoEvent(
                    AvisoDeArmadoDeGrupos.claveGrupoSinMentor(celula.id().value(), vigencia.hoyDe(celula, ahora)),
                    FaltaArmarGrupoEvent.Motivo.GRUPO_SIN_MENTOR, celula.cohorteId().value(), celula.id().value(),
                    "Un grupo en curso no tiene mentor", AvisoDeArmadoDeGrupos.cuerpoGrupoSinMentor(celula.nombre()),
                    ahora));
            publicados++;
        }
        if (publicados > 0) {
            log.info("[community.AvisosDeGruposSinMentorService] {} grupo(s) en curso sin mentor avisados", publicados);
        }
        return publicados;
    }

    /** Regular, en curso, sin mentor vigente y ya en horario de aviso en la zona del grupo. */
    private boolean tocaAvisar(Celula celula, Instant ahora) {
        if (celula.esRecepcion() || !vigencia.enCurso(celula, ahora)) {
            return false;
        }
        if (!AvisoDeArmadoDeGrupos.esHoraDeAvisar(ahora.atZone(vigencia.zonaDe(celula.cohorteId())).toLocalTime())) {
            return false;
        }
        return ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celula.id()))
                .mentorVigenteEn(celula.id(), ahora)
                .isEmpty();
    }
}
