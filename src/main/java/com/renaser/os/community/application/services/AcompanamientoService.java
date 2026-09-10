package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarAprendicesDelGrupoUseCase;
import com.renaser.os.community.application.ports.in.acompanamiento.ConsultarContextoAcompanamientoUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadPoliticaMentoriaPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.domain.model.acompanamiento.AsignacionCelula;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Comparator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

/**
 * Lecturas de acompañamiento centradas en el actor autenticado.
 *
 * <p>La autorización es implícita y por construcción: todo arranca de las asignaciones <b>del
 * actor</b>, así que no hay un {@code groupId} de entrada que alguien pueda manipular para
 * mirar otro grupo. Los endpoints que sí reciben un grupo por parámetro tendrán que
 * comprobar la relación vigente antes de responder (contracts.md, matriz de acceso).
 */
@Service
public class AcompanamientoService
        implements ConsultarContextoAcompanamientoUseCase, ConsultarAprendicesDelGrupoUseCase {

    /** Tope tecnico de pagina. No es el cupo comercial: la recepcion no tiene tope y hay que paginarla igual. */
    private static final int LIMITE_MAXIMO = 100;

    private final LoadAsignacionesPort loadAsignacionesPort;
    private final LoadCelulaPort loadCelulaPort;
    private final LoadPoliticaMentoriaPort loadPoliticaMentoriaPort;
    private final ParticipacionProgramaFinder participacionProgramaFinder;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    public AcompanamientoService(LoadAsignacionesPort loadAsignacionesPort, LoadCelulaPort loadCelulaPort,
                                  LoadPoliticaMentoriaPort loadPoliticaMentoriaPort,
                                  ParticipacionProgramaFinder participacionProgramaFinder,
                                  UserSummaryFinder userSummaryFinder, Clock clock) {
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.loadCelulaPort = loadCelulaPort;
        this.loadPoliticaMentoriaPort = loadPoliticaMentoriaPort;
        this.participacionProgramaFinder = participacionProgramaFinder;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ContextoAcompanamiento contexto(UserId actorId) {
        Instant ahora = clock.now();

        Optional<ParticipacionPrograma> participacion = participacionProgramaFinder.deParticipante(actorId);
        boolean participa = participacion.map(ParticipacionPrograma::inscrito).orElse(false);
        // null y no 0: "no participa" y "va por el dia 0" son cosas distintas y el cliente
        // tiene que poder diferenciarlas (plan.md §7, los nulos no se convierten en cero).
        Integer diaDePrograma = participa ? participacion.get().diaPrograma() : null;

        List<AsignacionResumen> asignaciones = new ArrayList<>();
        Map<CelulaId, ConjuntoAsignaciones> composicionPorGrupo = new HashMap<>();

        for (AsignacionCelula mia : loadAsignacionesPort.porUsuario(actorId)) {
            if (!mia.vigenteEn(ahora) || !esDeAcompanamiento(mia)) {
                continue;
            }
            Optional<Celula> grupo = loadCelulaPort.porId(mia.celulaId());
            if (grupo.isEmpty()) {
                continue;
            }
            // Una consulta por grupo, no una por miembro: el resto se resuelve en memoria.
            ConjuntoAsignaciones composicion = composicionPorGrupo.computeIfAbsent(mia.celulaId(),
                    id -> ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(id)));
            asignaciones.add(resumir(mia, grupo.get(), composicion, ahora));
        }

        UserRole rol = participacion.map(ParticipacionPrograma::rol).orElse(UserRole.TRAINEE);
        boolean acompana = !asignaciones.isEmpty();
        return new ContextoAcompanamiento(participa, diaDePrograma, acompana,
                new Capacidades(rol == UserRole.TRAINEE, esStaff(rol) && !participa, acompana),
                asignaciones);
    }


    @Override
    @Transactional(readOnly = true)
    public PaginaAprendices aprendices(ConsultaAprendices consulta) {
        Instant ahora = clock.now();
        CelulaId grupoId = CelulaId.of(consulta.grupoId());

        ConjuntoAsignaciones composicion = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(grupoId));
        requireAcompanaVigente(consulta.actorId(), grupoId, composicion, ahora);

        Celula grupo = loadCelulaPort.porId(grupoId)
                .orElseThrow(() -> new NoSuchElementException("Grupo no encontrado: " + consulta.grupoId()));

        List<UserId> vigentes = composicion.aprendicesVigentesEn(grupoId, ahora);
        // findByIds y no findById en un bucle: diez aprendices no justifican diez consultas,
        // y la recepcion puede tener cientos (CLAUDE.MD, operaciones en lote).
        Map<UserId, UserSummary> perfiles = userSummaryFinder.findByIds(vigentes);
        List<AprendizDelGrupo> todos = vigentes.stream()
                .map(perfiles::get)
                .filter(java.util.Objects::nonNull)
                .map(AcompanamientoService::aAprendiz)
                // Orden estable: por nombre y, ante homonimos, por id. Sin esto el cursor
                // saltea o repite filas entre paginas.
                .sorted(Comparator.comparing((AprendizDelGrupo a) -> a.nombre() == null ? "" : a.nombre())
                        .thenComparing(a -> a.participanteId().toString()))
                .toList();

        int desde = posicionDelCursor(todos, consulta.cursor());
        int limite = Math.clamp(consulta.limite(), 1, LIMITE_MAXIMO);
        int hasta = Math.min(desde + limite, todos.size());
        List<AprendizDelGrupo> pagina = todos.subList(desde, hasta);
        UUID siguiente = hasta < todos.size() ? pagina.getLast().participanteId() : null;

        return new PaginaAprendices(grupo.id().value(), grupo.nombre(),
                composicion.coberturaEn(grupoId, ahora).name(), todos.size(), pagina, siguiente);
    }

    /**
     * La comprobacion que impide que manipular el {@code groupId} devuelva gente de otro grupo.
     * Se pide relacion VIGENTE: un exmentor con un token todavia valido no pasa (V11, V12).
     */
    private void requireAcompanaVigente(UserId actorId, CelulaId grupoId, ConjuntoAsignaciones composicion,
                                         Instant ahora) {
        boolean acompana = composicion.todas().stream()
                .filter(a -> a.usuarioId().equals(actorId))
                .filter(a -> a.celulaId().equals(grupoId))
                .filter(AcompanamientoService::esDeAcompanamiento)
                .anyMatch(a -> a.vigenteEn(ahora));
        if (!acompana) {
            // Mismo mensaje tanto si el grupo no existe como si existe y no es suyo: distinguirlos
            // le confirmaria a quien prueba ids que ese grupo existe.
            throw new NotAuthorizedException("No acompanas ese grupo");
        }
    }

    private static AprendizDelGrupo aAprendiz(UserSummary usuario) {
        return new AprendizDelGrupo(usuario.id().value(), usuario.fullName(), usuario.avatarUrl(),
                usuario.status() == UserStatus.ACTIVE);
    }

    private static int posicionDelCursor(List<AprendizDelGrupo> todos, UUID cursor) {
        if (cursor == null) {
            return 0;
        }
        for (int i = 0; i < todos.size(); i++) {
            if (todos.get(i).participanteId().equals(cursor)) {
                return i + 1;
            }
        }
        // Cursor de una composicion anterior: se empieza de cero en vez de devolver vacio.
        return 0;
    }

    /**
     * Roles para los que el programa de 90 días es opcional (D-07). Se compara contra el enum
     * y no contra una lista de nombres: los roles del proyecto viven en dos idiomas —castellano
     * en la base, inglés en Java— y comparar cadenas es como se cuela un rol que no matchea.
     */
    private static boolean esStaff(UserRole rol) {
        return rol == UserRole.MENTOR || rol == UserRole.MENTOR_LEAD
                || rol == UserRole.ADMIN || rol == UserRole.ALCHEMIST;
    }

    /**
     * Ser APRENDIZ de un grupo no es acompañar. Un mentor que además cursa aparece acá solo
     * por su función de acompañamiento; su programa personal viaja aparte y sus métricas
     * nunca se mezclan con las de sus alumnos (plan.md §10).
     */
    private static boolean esDeAcompanamiento(AsignacionCelula asignacion) {
        return !asignacion.funcion().consumeCupo();
    }

    private AsignacionResumen resumir(AsignacionCelula mia, Celula grupo, ConjuntoAsignaciones composicion,
                                       Instant ahora) {
        PoliticaMentoria politica = loadPoliticaMentoriaPort.porCohorte(grupo.cohorteId())
                .orElseGet(() -> PoliticaMentoria.porDefecto(grupo.cohorteId()));

        int aprendices = composicion.aprendicesVigentesEn(grupo.id(), ahora).size();
        Integer cupo = grupo.cupo(politica.capacidadCelula()).maximo().orElse(null);

        return new AsignacionResumen(
                grupo.id().value(),
                grupo.nombre(),
                grupo.cohorteId().value(),
                grupo.tipo(),
                mia.funcion(),
                mia.periodo().inicio(),
                mia.periodo().fin(),
                composicion.coberturaEn(grupo.id(), ahora),
                aprendices,
                cupo);
    }
}
