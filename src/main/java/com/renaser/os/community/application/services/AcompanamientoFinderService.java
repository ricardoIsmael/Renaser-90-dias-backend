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
        if (!grupoOperativoEn(grupoId, instante)) {
            return List.of();
        }
        return asignacionesDe(grupoId).stream()
                .filter(a -> a.vigenteEn(instante))
                .map(AsignacionCelula::usuarioId)
                .distinct()
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean esIntegranteVigente(UUID grupoId, UserId usuarioId, Instant instante) {
        return grupoOperativoEn(grupoId, instante)
                && asignacionesDe(grupoId).stream()
                .filter(a -> a.usuarioId().equals(usuarioId))
                .anyMatch(a -> a.vigenteEn(instante));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean acompanaVigente(UserId actorId, UUID grupoId, Instant instante) {
        return grupoOperativoEn(grupoId, instante)
                && asignacionesDe(grupoId).stream()
                .filter(a -> a.usuarioId().equals(actorId))
                // Ser APRENDIZ del grupo no es acompanarlo.
                .filter(a -> !a.funcion().consumeCupo())
                .anyMatch(a -> a.vigenteEn(instante));
    }

    /**
     * Si el grupo esta DENTRO de su periodo ese instante. Sin periodo, siempre — asi quedaron
     * todas las celulas anteriores a V48 y no se les pone fecha de muerte.
     *
     * <p><b>Por que las tres preguntas de pertenencia pasan por aca (SDD 003, ARF-18 / V17).</b>
     * Cerrar el periodo de un grupo NO cierra sus filas de {@code asignaciones_celula} —son dos
     * hechos distintos y el historial tiene que conservarse—, asi que mirar solo la asignacion
     * dejaba el chat de un grupo terminado abierto y a su exmentor leyendo la semana de sus
     * exalumnos. Poner la condicion aca y no en un job hace que la revocacion sea inmediata e
     * idempotente: no depende de que ningun barrido haya corrido, que es justo lo que el
     * requisito pide. Un grupo PROGRAMADO tampoco pasa: existir no es estar corriendo.
     *
     * <p>Lo que NO se filtra son los tramos historicos ({@code tramosDeMentor},
     * {@code tramosDeAprendices}) ni {@code aprendicesVigentes}: la evaluacion de un mes tiene que
     * poder mirar un grupo que ya cerro, o el mentor perderia la nota del mes que si acompaño.
     */
    private boolean grupoOperativoEn(UUID grupoId, Instant instante) {
        return loadCelulaPort.porId(CelulaId.of(grupoId))
                .map(celula -> celula.vigenteEn(diaDelPrograma(celula, instante)))
                .orElse(false);
    }

    /**
     * El dia del GRUPO, en la zona de la politica de su cohorte. No es la del servidor ni la del
     * telefono: si la decidiera el cliente, dos aprendices en husos distintos verian cerrar el
     * mismo grupo en dias distintos (plan.md §3).
     */
    private java.time.LocalDate diaDelPrograma(Celula celula, Instant instante) {
        String zona = loadPoliticaMentoriaPort.porCohorte(celula.cohorteId())
                .orElseGet(() -> PoliticaMentoria.porDefecto(celula.cohorteId()))
                .zonaHoraria();
        return instante.atZone(java.time.ZoneId.of(zona)).toLocalDate();
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
            // Un grupo fuera de su periodo no genera avisos de inactividad: no esta corriendo.
            if (!celula.vigenteEn(diaDelPrograma(celula, instante))) {
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
