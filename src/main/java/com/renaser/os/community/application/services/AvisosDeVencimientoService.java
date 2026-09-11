package com.renaser.os.community.application.services;

import com.renaser.os.community.api.GrupoPorVencerEvent;
import com.renaser.os.community.application.ports.in.celula.DetectarGruposPorVencerUseCase;
import com.renaser.os.community.application.ports.out.celula.ConsultarGruposPorVencerPort;
import com.renaser.os.community.application.ports.out.celula.ConsultarGruposPorVencerPort.GrupoQueVence;
import com.renaser.os.community.domain.model.celula.PeriodoGrupo;
import com.renaser.os.community.domain.model.celula.ReglasDeVencimientoDeGrupo;
import com.renaser.os.shared.domain.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
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

    /**
     * La zona en la que se decide que dia es hoy.
     *
     * <p>Un grupo es del programa, no de una persona: no hay "la zona del participante" a la que
     * acudir, y usar la del servidor haria que un despliegue en otra region moviera la fecha de
     * cierre de todos los grupos. Se fija la del programa, que es la misma que ya usa
     * {@code PoliticaMentoria.ZONA_POR_DEFECTO}.
     */
    private static final ZoneId ZONA_DEL_PROGRAMA = ZoneId.of("America/Lima");

    private final ConsultarGruposPorVencerPort consultarPort;
    private final ApplicationEventPublisher publisher;
    private final Clock clock;

    public AvisosDeVencimientoService(ConsultarGruposPorVencerPort consultarPort,
                                       ApplicationEventPublisher publisher, Clock clock) {
        this.consultarPort = consultarPort;
        this.publisher = publisher;
        this.clock = clock;
    }

    @Override
    @Transactional
    public int avisarDeLosQueVencen() {
        LocalDate hoy = clock.now().atZone(ZONA_DEL_PROGRAMA).toLocalDate();
        LocalDate hasta = hoy.plusDays(ReglasDeVencimientoDeGrupo.DIAS_DE_ANTELACION - 1L);

        // La consulta ya acota la ventana; la regla vuelve a preguntar por cada uno. No es
        // redundancia inutil: la consulta es una optimizacion —no traer mil grupos para descartar
        // 995— y la REGLA es la que define cuando toca avisar. Si algun dia divergen, manda la
        // regla, que es la que esta probada y la que alguien va a leer para entender el criterio.
        List<GrupoQueVence> candidatos = consultarPort.conCierreEntre(hoy, hasta);
        int publicados = 0;
        for (GrupoQueVence grupo : candidatos) {
            PeriodoGrupo periodo = new PeriodoGrupo(grupo.inicioDelPeriodo(), grupo.finDelPeriodo());
            if (!ReglasDeVencimientoDeGrupo.tocaAvisar(periodo, hoy)) {
                continue;
            }
            publisher.publishEvent(new GrupoPorVencerEvent(
                    ReglasDeVencimientoDeGrupo.claveDeDeduplicacion(grupo.celulaId(), grupo.finDelPeriodo()),
                    grupo.celulaId(), grupo.nombre(), grupo.finDelPeriodo(),
                    periodo.diasRestantesEn(hoy), clock.now()));
            publicados++;
        }
        if (publicados > 0) {
            log.info("[community.AvisosDeVencimientoService] {} grupo(s) por vencer avisados", publicados);
        }
        return publicados;
    }
}
