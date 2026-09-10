package com.renaser.os.community.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.PeriodoAsignacion;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Traduce el historial de asignaciones a records planos para los módulos de afuera.
 *
 * <p>Servicio propio y no un método más de {@code AcompanamientoService}, por el mismo criterio
 * que {@code evidence.RegistrosConEvidenciaFinderService}: es la superficie hacia otros módulos,
 * no escribe nada, y tenerla aparte deja a la vista qué expone {@code community}.
 */
@Service
class AcompanamientoFinderService implements AcompanamientoFinder {

    private final LoadAsignacionesPort loadAsignacionesPort;
    private final LoadCelulaPort loadCelulaPort;
    private final LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;

    AcompanamientoFinderService(LoadAsignacionesPort loadAsignacionesPort, LoadCelulaPort loadCelulaPort,
                                 LoadPoliticaMentoriaPort loadPoliticaMentoriaPort) {
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.loadCelulaPort = loadCelulaPort;
        this.loadPoliticaMentoriaPort = loadPoliticaMentoriaPort;
    }

    @Override
    @Transactional(readOnly = true)
    public List<TramoDeAcompanamiento> tramosDeMentor(UserId mentorId, Instant desde, Instant hasta) {
        PeriodoAsignacion ventana = ventana(desde, hasta);
        return loadAsignacionesPort.porUsuario(mentorId).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR)
                .flatMap(a -> a.periodo().interseccionCon(ventana).stream()
                        .map(tramo -> new TramoDeAcompanamiento(a.celulaId().value(),
                                loadCelulaPort.porId(a.celulaId()).map(Celula::nombre).orElse(null),
                                tramo.inicio(), tramo.fin())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserId> aprendicesVigentes(UUID grupoId, Instant instante) {
        return asignacionesDe(grupoId).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                .filter(a -> a.vigenteEn(instante))
                .map(AsignacionCelula::usuarioId)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TramoDeAprendiz> tramosDeAprendices(UUID grupoId, Instant desde, Instant hasta) {
        PeriodoAsignacion ventana = ventana(desde, hasta);
        return asignacionesDe(grupoId).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                .flatMap(a -> a.periodo().interseccionCon(ventana).stream()
                        .map(tramo -> new TramoDeAprendiz(a.usuarioId(), tramo.inicio(), tramo.fin())))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserId> integrantesVigentes(UUID grupoId, Instant instante) {
        return asignacionesDe(grupoId).stream()
                .filter(a -> a.vigenteEn(instante))
                .map(AsignacionCelula::usuarioId)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean esIntegranteVigente(UUID grupoId, UserId usuarioId, Instant instante) {
        return asignacionesDe(grupoId).stream()
                .filter(a -> a.usuarioId().equals(usuarioId))
                .anyMatch(a -> a.vigenteEn(instante));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean acompanaVigente(UserId actorId, UUID grupoId, Instant instante) {
        return asignacionesDe(grupoId).stream()
                .filter(a -> a.usuarioId().equals(actorId))
                // Ser APRENDIZ del grupo no es acompanarlo.
                .filter(a -> !a.funcion().consumeCupo())
                .anyMatch(a -> a.vigenteEn(instante));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<GrupoBasico> grupo(UUID grupoId) {
        return loadCelulaPort.porId(CelulaId.of(grupoId)).map(celula -> new GrupoBasico(
                celula.id().value(), celula.nombre(), celula.cohorteId().value(),
                loadPoliticaMentoriaPort.porCohorte(celula.cohorteId())
                        .orElseGet(() -> PoliticaMentoria.porDefecto(celula.cohorteId()))
                        .zonaHoraria()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrupoAcompanado> gruposConMentorVigente(Instant instante) {
        List<GrupoAcompanado> acompanados = new java.util.ArrayList<>();
        for (Celula celula : loadCelulaPort.todas()) {
            if (celula.esRecepcion()) {
                continue;
            }
            PoliticaMentoria politica = loadPoliticaMentoriaPort.porCohorte(celula.cohorteId())
                    .orElseGet(() -> PoliticaMentoria.porDefecto(celula.cohorteId()));
            asignacionesDe(celula.id().value()).stream()
                    .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR)
                    .filter(a -> a.vigenteEn(instante))
                    .findFirst()
                    .ifPresent(mentor -> acompanados.add(new GrupoAcompanado(celula.id().value(), celula.nombre(),
                            mentor.usuarioId(), celula.cohorteId().value(), politica.zonaHoraria(),
                            politica.diasSinActividadAlerta())));
        }
        return acompanados;
    }

    private List<AsignacionCelula> asignacionesDe(UUID grupoId) {
        return loadAsignacionesPort.porCelula(CelulaId.of(grupoId));
    }

    private static PeriodoAsignacion ventana(Instant desde, Instant hasta) {
        return hasta == null ? PeriodoAsignacion.abierto(desde) : PeriodoAsignacion.cerrado(desde, hasta);
    }
}
