package com.renaser.os.rag.infrastructure.adapter.out.programa;

import com.renaser.os.points.api.PorcentajeRocasFinder;
import com.renaser.os.points.api.ProximoEventoFinder;
import com.renaser.os.rag.application.ports.out.programa.ConsultarPanoramaDelProgramaPort;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Compone el panorama con los MISMOS contratos publicos que usa {@code GET /home}
 * ({@code points.HomeAgregadoService}), sin copiar su logica ni tocar tablas ajenas (D-41).
 *
 * <p><b>Falla parcial, mismo criterio que Inicio:</b> si el finder del proximo evento no aplica
 * para esta persona ({@link NoSuchElementException} o {@link NotAuthorizedException}, las dos que
 * documenta su contrato), el panorama sale igual, sin evento. Una herramienta que se cae entera
 * porque un widget no aplica le quita a la persona el dia y la hora, que si estaban.
 */
@Component
class ConsultarPanoramaDelProgramaAdapter implements ConsultarPanoramaDelProgramaPort {

    private static final Logger log = LoggerFactory.getLogger(ConsultarPanoramaDelProgramaAdapter.class);

    private final ParticipacionProgramaFinder participacionFinder;
    private final PorcentajeRocasFinder porcentajeRocasFinder;
    private final ProximoEventoFinder proximoEventoFinder;

    ConsultarPanoramaDelProgramaAdapter(ParticipacionProgramaFinder participacionFinder,
                                        PorcentajeRocasFinder porcentajeRocasFinder,
                                        ProximoEventoFinder proximoEventoFinder) {
        this.participacionFinder = participacionFinder;
        this.porcentajeRocasFinder = porcentajeRocasFinder;
        this.proximoEventoFinder = proximoEventoFinder;
    }

    @Override
    public Optional<Panorama> de(UserId participanteId, Instant ahora) {
        return participacionFinder.deParticipante(participanteId)
                .map(participacion -> panoramaDe(participanteId, participacion, ahora));
    }

    private Panorama panoramaDe(UserId participanteId, ParticipacionPrograma participacion, Instant ahora) {
        LocalDate hoyEnSuZona = ahora.atZone(participacion.zona()).toLocalDate();
        return new Panorama(participacion.zona(), coherenciaHasta(participanteId, hoyEnSuZona),
                proximoEventoDe(participanteId));
    }

    /**
     * El "hasta" es hoy EN LA ZONA DEL PARTICIPANTE, igual que {@code HomeAgregadoService.coherenciaDe}
     * (regla 02 §1). Sin clave en el mapa es "no planifico nada en la semana": se devuelve vacio,
     * nunca un cero ni un cien (D-128).
     */
    private Optional<BigDecimal> coherenciaHasta(UserId participanteId, LocalDate hoyEnSuZona) {
        return Optional.ofNullable(porcentajeRocasFinder
                .porcentajePorParticipante(List.of(participanteId), hoyEnSuZona).get(participanteId));
    }

    private Optional<ProximoEvento> proximoEventoDe(UserId participanteId) {
        try {
            return proximoEventoFinder.proximoEventoDe(participanteId)
                    .map(evento -> new ProximoEvento(evento.titulo(), evento.iniciaEn()));
        } catch (NoSuchElementException | NotAuthorizedException noAplica) {
            log.info("[rag] el proximo evento no aplica para este participante: {}",
                    noAplica.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
