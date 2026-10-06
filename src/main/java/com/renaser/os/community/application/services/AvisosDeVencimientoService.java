package com.renaser.os.community.application.services;

import com.renaser.os.community.api.GrupoPorVencerEvent;
import com.renaser.os.community.application.ports.in.celula.DetectarGruposPorVencerUseCase;
import com.renaser.os.community.application.ports.out.celula.ConsultarGruposPorVencerPort;
import com.renaser.os.community.application.ports.out.celula.ConsultarGruposPorVencerPort.GrupoQueVence;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.domain.model.celula.PeriodoGrupo;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.community.domain.model.celula.ReglasDeVencimientoDeGrupo;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Avisa al administrador de los grupos que estan por cerrar su periodo.
 *
 * <p>El aviso es lo que convierte un cierre automatico en una decision: sin el, el grupo se cierra
 * el dia que dice su periodo, sus alumnos dejan de verlo, y eso ocurre en silencio hasta que
 * alguien se acuerda de programar el siguiente.
 */
@Service
public class AvisosDeVencimientoService implements DetectarGruposPorVencerUseCase {

    private static final Logger log = LoggerFactory.getLogger(AvisosDeVencimientoService.class);

    private final ConsultarGruposPorVencerPort consultarPort;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;
    private final VigenciaDeGrupos vigencia;

    public AvisosDeVencimientoService(ConsultarGruposPorVencerPort consultarPort,
                                       LoadPoliticaMentoriaPort politicas,
                                       ApplicationEventPublisher publisher, Clock clock) {
        this.consultarPort = consultarPort;
        this.publisher = publisher;
        this.clock = clock;
        this.vigencia = new VigenciaDeGrupos(politicas);
    }

    @Override
    @Transactional
    public int avisarDeLosQueVencen() {
        Instant ahora = clock.now();
        int publicados = 0;
        for (GrupoQueVence grupo : candidatosPosibles(ahora)) {
            LocalDate hoy = hoyDelGrupo(grupo, ahora);
            PeriodoGrupo periodo = new PeriodoGrupo(grupo.inicioDelPeriodo(), grupo.finDelPeriodo());
            if (!ReglasDeVencimientoDeGrupo.tocaAvisar(periodo, hoy)) {
                continue;
            }
            publisher.publishEvent(new GrupoPorVencerEvent(
                    ReglasDeVencimientoDeGrupo.claveDeDeduplicacion(grupo.celulaId(), grupo.finDelPeriodo()),
                    grupo.celulaId(), grupo.nombre(), grupo.finDelPeriodo(),
                    periodo.diasRestantesEn(hoy), ahora));
            publicados++;
        }
        if (publicados > 0) {
            log.info("[community.AvisosDeVencimientoService] {} grupo(s) por vencer avisados", publicados);
        }
        return publicados;
    }

    /**
     * La consulta solo acota con una cota gruesa (un dia de holgura por lado, porque el dia local
     * de una cohorte difiere hasta en uno del UTC); la REGLA decide por grupo, en la zona de su
     * cohorte. No es redundancia inutil: la consulta es una optimizacion --no traer mil grupos para
     * descartar 995-- y la regla es la que define cuando toca avisar. Si divergen, manda la regla.
     */
    private List<GrupoQueVence> candidatosPosibles(Instant ahora) {
        LocalDate hoyUtc = ahora.atZone(ZoneOffset.UTC).toLocalDate();
        return consultarPort.conCierreEntre(hoyUtc.minusDays(1),
                hoyUtc.plusDays(ReglasDeVencimientoDeGrupo.DIAS_DE_ANTELACION));
    }

    /** El dia de ese instante para el grupo: en la zona de su cohorte, no la del servidor (E-91, E-565). */
    private LocalDate hoyDelGrupo(GrupoQueVence grupo, Instant ahora) {
        return ahora.atZone(vigencia.zonaDe(CohorteId.of(grupo.cohorteId()))).toLocalDate();
    }
}
