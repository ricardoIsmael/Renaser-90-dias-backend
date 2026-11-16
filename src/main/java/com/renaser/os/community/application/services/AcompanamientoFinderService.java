package com.renaser.os.community.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.community.api.AprendicesDelAcompananteFinder;
import com.renaser.os.community.api.MentorVigenteFinder;
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
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Traduce el historial de asignaciones a records planos para los módulos de afuera.
 *
 * <p>Servicio propio y no un método más de {@code AcompanamientoService}, por el mismo criterio
 * que {@code evidence.RegistrosConEvidenciaFinderService}: es la superficie hacia otros módulos,
 * no escribe nada, y tenerla aparte deja a la vista qué expone {@code community}.
 */
@Service
class AcompanamientoFinderService implements AcompanamientoFinder, MentorVigenteFinder, AprendicesDelAcompananteFinder {

    private final LoadAsignacionesPort loadAsignacionesPort;
    private final LoadCelulaPort loadCelulaPort;
    private final LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;
    private final VigenciaDeGrupos vigencia;
    /** Spec 022: quien tiene varias formaciones acompaña solo los grupos de la que tiene activa. */
    private final AlcanceDeFormacionFinder formaciones;

    AcompanamientoFinderService(LoadAsignacionesPort loadAsignacionesPort, LoadCelulaPort loadCelulaPort,
                                 LoadPoliticaMentoriaPort loadPoliticaMentoriaPort, AlcanceDeFormacionFinder formaciones) {
        this.formaciones = formaciones;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.loadCelulaPort = loadCelulaPort;
        this.loadPoliticaMentoriaPort = loadPoliticaMentoriaPort;
        this.vigencia = new VigenciaDeGrupos(loadPoliticaMentoriaPort);
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
    public List<UserId> acompanantesVigentes(UUID grupoId, Instant instante) {
        if (!grupoOperativoEn(grupoId, instante)) {
            return List.of();
        }
        return asignacionesDe(grupoId).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR || a.funcion() == FuncionAcompanamiento.GUIA)
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
                .anyMatch(a -> a.vigenteEn(instante))
                // Spec 022: un grupo de la formación que el actor no tiene activa se responde como un grupo ajeno.
                && enLaFormacionDe(actorId, grupoId);
    }

    /**
     * Los grupos donde la persona tiene hoy una asignación que no es de aprendiz salen de SU historial (una consulta); de cada uno se
     * pregunta {@link #acompanaVigente} —la misma pregunta del padrón del grupo: en curso y en la formación activa de quien acompaña— y,
     * si la respuesta es sí, se toman sus aprendices vigentes. Así «a quién acompaña» y «qué grupo puede abrir» nunca difieren.
     */
    @Override
    @Transactional(readOnly = true)
    public Set<UserId> aprendicesQueAcompana(UserId acompanante, Instant instante) {
        Set<UserId> aprendices = new LinkedHashSet<>();
        Set<CelulaId> consultados = new HashSet<>();
        for (AsignacionCelula mia : loadAsignacionesPort.porUsuario(acompanante)) {
            // Ser APRENDIZ de un grupo no es acompañarlo, y lo que ya terminó tampoco.
            if (mia.funcion().consumeCupo() || !mia.vigenteEn(instante) || !consultados.add(mia.celulaId())) {
                continue;
            }
            UUID grupoId = mia.celulaId().value();
            if (acompanaVigente(acompanante, grupoId, instante)) {
                aprendices.addAll(aprendicesVigentes(grupoId, instante));
            }
        }
        return aprendices;
    }

    private boolean enLaFormacionDe(UserId actorId, UUID grupoId) {
        return loadCelulaPort.porId(CelulaId.of(grupoId)).map(grupo -> formaciones.de(actorId).incluye(grupo.formacion())).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean grupoOperativo(UUID grupoId, Instant instante) {
        return grupoOperativoEn(grupoId, instante);
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
                .map(celula -> vigencia.enCurso(celula, instante))
                .orElse(false);
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
            if (!vigencia.enCurso(celula, instante)) {
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
                            politica.diasSinActividadAlerta(), celula.formacion())));
        }
        return acompanados;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrupoAcompanado> gruposRegularesEnCurso(Instant instante) {
        List<GrupoAcompanado> enCurso = new ArrayList<>();
        for (Celula celula : loadCelulaPort.todas()) {
            if (!celula.esRecepcion() && vigencia.enCurso(celula, instante)) {
                enCurso.add(aGrupoAcompanado(celula, mentorVigenteDe(celula.id().value(), instante)));
            }
        }
        return enCurso;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrupoAcompanado> gruposRegularesEnCursoEntre(LocalDate desde, LocalDate hasta) {
        List<GrupoAcompanado> delPeriodo = new ArrayList<>();
        for (Celula celula : loadCelulaPort.todas()) {
            if (!celula.esRecepcion() && celula.enCursoAlgunDiaEntre(desde, hasta)) {
                delPeriodo.add(aGrupoAcompanado(celula, mentorDelPeriodo(celula, desde, hasta)));
            }
        }
        return delPeriodo;
    }

    /**
     * El mentor que acompaño al grupo en esos dias: de los tramos de MENTOR que tocan la ventana, el
     * que empezo ultimo. Si hubo relevo a mitad del mes, el ranking nombra a quien lo cerro.
     */
    private UserId mentorDelPeriodo(Celula celula, LocalDate desde, LocalDate hasta) {
        ZoneId zona = vigencia.zonaDe(celula.cohorteId());
        PeriodoAsignacion ventana = PeriodoAsignacion.cerrado(desde.atStartOfDay(zona).toInstant(),
                hasta.plusDays(1).atStartOfDay(zona).toInstant());
        return asignacionesDe(celula.id().value()).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR)
                .filter(a -> a.periodo().solapaCon(ventana))
                .max(Comparator.comparing(a -> a.periodo().inicio()))
                .map(AsignacionCelula::usuarioId)
                .orElse(null);
    }

    private GrupoAcompanado aGrupoAcompanado(Celula celula, UserId mentorId) {
        PoliticaMentoria politica = loadPoliticaMentoriaPort.porCohorte(celula.cohorteId())
                .orElseGet(() -> PoliticaMentoria.porDefecto(celula.cohorteId()));
        return new GrupoAcompanado(celula.id().value(), celula.nombre(), mentorId, celula.cohorteId().value(),
                politica.zonaHoraria(), politica.diasSinActividadAlerta(), celula.formacion());
    }

    @Override
    @Transactional(readOnly = true)
    public List<GrupoConAprendices> gruposOperativos(Instant instante) {
        List<Celula> enCurso = loadCelulaPort.todas().stream().filter(celula -> vigencia.enCurso(celula, instante)).toList();
        // H-04c: las asignaciones de todos los grupos en UNA consulta, no una por grupo (1 + C).
        Map<CelulaId, List<AsignacionCelula>> asignaciones = loadAsignacionesPort.porCelulas(enCurso.stream().map(Celula::id).toList());
        List<GrupoConAprendices> operativos = new java.util.ArrayList<>();
        for (Celula celula : enCurso) {
            List<AsignacionCelula> vigentes = asignaciones.getOrDefault(celula.id(), List.of()).stream()
                    .filter(a -> a.vigenteEn(instante))
                    .toList();
            UserId mentor = vigentes.stream()
                    .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR)
                    .map(AsignacionCelula::usuarioId)
                    .findFirst()
                    .orElse(null);
            List<UserId> aprendices = vigentes.stream()
                    .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ)
                    .map(AsignacionCelula::usuarioId)
                    .distinct()
                    .toList();
            operativos.add(new GrupoConAprendices(celula.id().value(), celula.nombre(), celula.esRecepcion(),
                    mentor, aprendices, celula.formacion()));
        }
        return operativos;
    }

    /**
     * D-221: el nombre del chat de cada grupo sale de su mentor vigente. No filtra por grupo operativo:
     * un grupo cerrado conserva el nombre con su mentor mientras la asignación siga abierta, y el acceso
     * a ese chat ya lo corta la pertenencia vigente, no el nombre.
     */
    @Override
    @Transactional(readOnly = true)
    public List<GrupoConSuMentor> deLosGrupos(Collection<UUID> grupoIds, Instant instante) {
        List<GrupoConSuMentor> resultado = new ArrayList<>();
        for (UUID grupoId : new LinkedHashSet<>(grupoIds)) {
            loadCelulaPort.porId(CelulaId.of(grupoId)).ifPresent(celula -> resultado.add(
                    new GrupoConSuMentor(grupoId, celula.nombre(), mentorVigenteDe(grupoId, instante), celula.formacion())));
        }
        return resultado;
    }

    private UserId mentorVigenteDe(UUID grupoId, Instant instante) {
        return asignacionesDe(grupoId).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.MENTOR)
                .filter(a -> a.vigenteEn(instante))
                .map(AsignacionCelula::usuarioId)
                .findFirst()
                .orElse(null);
    }

    private List<AsignacionCelula> asignacionesDe(UUID grupoId) {
        return loadAsignacionesPort.porCelula(CelulaId.of(grupoId));
    }

    private static PeriodoAsignacion ventana(Instant desde, Instant hasta) {
        return hasta == null ? PeriodoAsignacion.abierto(desde) : PeriodoAsignacion.cerrado(desde, hasta);
    }
}
