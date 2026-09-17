package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.celula.ConsultarCelulasUseCase.PerfilBasico;
import com.renaser.os.community.application.ports.in.celula.ConsultarMiCelulaUseCase.MiCelula;
import com.renaser.os.community.application.ports.in.celula.ConsultarMisCelulasUseCase;
import com.renaser.os.community.application.ports.out.acompanamiento.LoadAsignacionesPort;
import com.renaser.os.community.application.ports.out.celula.LoadCelulaPort;
import com.renaser.os.community.application.ports.out.cohorte.LoadCohortePort;
import com.renaser.os.community.application.ports.out.participante.ConsultarCelulaDeParticipantePort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.domain.model.acompanamiento.ConjuntoAsignaciones;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.celula.Celula;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.Cohorte;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Los grupos del aprendiz en plural, cada uno con SU gente (D-142).
 *
 * <p><b>Por qué una clase aparte y no dos métodos en {@code CelulaService}.</b> Ese servicio ya pasa
 * con holgura el techo de 300 líneas de {@code .claude/rules/01} y concentra el panel de
 * administración entero; sumarle la vista del aprendiz lo empuja más lejos todavía. Mismo criterio
 * con el que {@code SumarAprendizAGrupoService} y {@code SumarMentorAGrupoService} salieron de
 * {@code ComposicionDeCelulaService}.
 *
 * <p><b>Todo lo que se lee acá sale del historial</b> ({@code asignaciones_celula}), nunca del
 * puntero {@code participantes_programa.celula_id}. El puntero solo se usa para una cosa: decidir
 * cuál de los grupos es el principal, y con eso ordenar la lista. Preguntarle a una columna de un
 * solo valor "¿en qué grupos está?" es lo que producía el bug.
 */
@Service
public class MisCelulasService implements ConsultarMisCelulasUseCase {

    private final LoadCelulaPort loadCelulaPort;
    private final LoadCohortePort loadCohortePort;
    private final LoadAsignacionesPort loadAsignacionesPort;
    private final ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort;
    private final ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;

    public MisCelulasService(LoadCelulaPort loadCelulaPort, LoadCohortePort loadCohortePort,
                              LoadAsignacionesPort loadAsignacionesPort,
                              ConsultarCelulaDeParticipantePort consultarCelulaDeParticipantePort,
                              ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort,
                              UserSummaryFinder userSummaryFinder, Clock clock) {
        this.loadCelulaPort = loadCelulaPort;
        this.loadCohortePort = loadCohortePort;
        this.loadAsignacionesPort = loadAsignacionesPort;
        this.consultarCelulaDeParticipantePort = consultarCelulaDeParticipantePort;
        this.consultarPerfilUsuarioPort = consultarPerfilUsuarioPort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<MiCelula> misCelulas(UserId actorId) {
        requireActorActivo(actorId);
        Instant ahora = clock.now();
        CelulaId principal = consultarCelulaDeParticipantePort.celulaDeUsuario(actorId).orElse(null);

        return gruposVigentesDe(actorId, ahora).stream()
                .map(loadCelulaPort::porId)
                .flatMap(Optional::stream)
                .filter(celula -> !celula.vencidoEn(hoyDelPrograma()))
                .sorted(ordenConElPrincipalPrimero(principal))
                .map(celula -> aMiCelula(celula, ahora))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PerfilBasico> integrantesDe(UserId actorId, CelulaId celulaId) {
        requireActorActivo(actorId);
        Instant ahora = clock.now();
        ConjuntoAsignaciones delGrupo = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celulaId));
        boolean estaAdentro = delGrupo.aprendicesVigentesEn(celulaId, ahora).contains(actorId)
                || delGrupo.mentorVigenteEn(celulaId, ahora).filter(actorId::equals).isPresent();
        if (!estaAdentro) {
            /* No se responde lista vacia: una lista vacia es indistinguible de "el grupo no tiene a
               nadie", y con eso cualquiera podria barrer ids de grupo para inferir cuales existen y
               cuales estan poblados. Un grupo ajeno es un 403.

               El mentor del grupo entra por la misma puerta que sus aprendices: acompana a esa
               gente, ya los ve en el panel y en el chat, y dejarlo afuera de la unica lectura que
               responde "quienes son" seria una asimetria sin motivo. */
            throw new NotAuthorizedException("No perteneces a ese grupo");
        }
        return delGrupo.aprendicesVigentesEn(celulaId, ahora).stream().map(this::perfilBasico).toList();
    }

    /**
     * El principal primero; el resto por nombre.
     *
     * <p>El puntero puede nombrar un grupo que ya no esta en la lista —vencido, o del que lo
     * sacaron sin repuntar—: en ese caso ninguno gana el desempate y quedan todos por nombre, que
     * sigue siendo un orden estable. No se inventa un principal.
     */
    private static Comparator<Celula> ordenConElPrincipalPrimero(CelulaId principal) {
        return Comparator.<Celula, Boolean>comparing(celula -> !celula.id().equals(principal))
                .thenComparing(Celula::nombre, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    /**
     * Los grupos en los que la persona esta hoy, sea cursando o acompanando.
     *
     * <p><b>Por que MENTOR tambien y no solo APRENDIZ.</b> Porque si no, un mentor recibe lista
     * vacia, y ese es exactamente el pozo en el que cayo el intento anterior de arreglar la
     * cabecera del chat de grupo en la app: se probo sacar el numero de integrantes de
     * {@code /me/cell}, que responde "¿de que grupo soy MIEMBRO?", y a un mentor le contestaba
     * "de ninguno". La pregunta que hace esta lectura no es esa, es "¿en que grupos estoy?" — y un
     * mentor esta en los que acompana, ahora incluso en varios (D-141).
     *
     * <p>GUIA y SOPORTE quedan afuera a proposito: cubren grupos como staff, no pertenecen a
     * ellos, y meterlos aca le llenaria la app a un administrador con todos los grupos que toca.
     */
    private List<CelulaId> gruposVigentesDe(UserId usuarioId, Instant ahora) {
        return loadAsignacionesPort.porUsuario(usuarioId).stream()
                .filter(a -> a.funcion() == FuncionAcompanamiento.APRENDIZ
                        || a.funcion() == FuncionAcompanamiento.MENTOR)
                .filter(a -> a.vigenteEn(ahora))
                .map(a -> a.celulaId())
                .distinct()
                .toList();
    }

    /**
     * El conteo tambien sale del historial, y eso importa: {@code /me/cell} lo saca del puntero, asi
     * que un grupo podia decir "5 integrantes" y listar 3. Acá el numero y la lista responden la
     * misma pregunta a la misma tabla.
     */
    private MiCelula aMiCelula(Celula celula, Instant ahora) {
        Cohorte cohorte = requireCohorte(celula);
        PerfilBasico mentor = celula.mentorId() != null ? perfilBasico(celula.mentorId()) : null;
        int cantidadMiembros = ConjuntoAsignaciones.de(loadAsignacionesPort.porCelula(celula.id()))
                .aprendicesVigentesEn(celula.id(), ahora).size();
        int totalCelulas = loadCelulaPort.porCohorte(celula.cohorteId()).size();
        return new MiCelula(celula, cohorte, mentor, cantidadMiembros, totalCelulas);
    }

    private Cohorte requireCohorte(Celula celula) {
        return loadCohortePort.porId(celula.cohorteId())
                .orElseThrow(() -> new NoSuchElementException("Cohorte no encontrada: " + celula.cohorteId()));
    }

    private PerfilBasico perfilBasico(UserId usuarioId) {
        return consultarPerfilUsuarioPort.porId(usuarioId)
                .map(p -> new PerfilBasico(p.id(), p.nombreCompleto(), p.avatarUrl()))
                .orElse(new PerfilBasico(usuarioId, null, null));
    }

    /** Copia deliberada de {@code CelulaService} (criterio de D-126): hoy piden lo mismo, y si
     * manana una de las dos autorizaciones cambia, cambia sola. */
    private void requireActorActivo(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
    }

    /** Regla 02: el dia se mira en la zona del programa, nunca con la fecha del servidor. */
    private LocalDate hoyDelPrograma() {
        return clock.now().atZone(ZoneId.of(PoliticaMentoria.ZONA_POR_DEFECTO)).toLocalDate();
    }
}
