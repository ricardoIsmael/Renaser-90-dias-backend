package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.NotaDelAprendiz;
import com.renaser.os.mentoring.api.NotasDelMentorFinder;
import com.renaser.os.mentoring.application.ports.in.ConsultarNotasDelAprendizUseCase;
import com.renaser.os.mentoring.application.ports.in.CorregirNotaDelAprendizUseCase;
import com.renaser.os.mentoring.application.ports.in.EscribirNotaDelAprendizUseCase;
import com.renaser.os.mentoring.application.ports.out.GuardarNotaDelMentorPort;
import com.renaser.os.mentoring.application.ports.out.LeerNotasDelMentorPort;
import com.renaser.os.mentoring.domain.model.nota.NotaDelMentor;
import com.renaser.os.mentoring.domain.model.nota.VersionDeLaNota;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Las notas privadas del mentor sobre un aprendiz (spec 055, FR-023 a FR-028).
 *
 * <p>Autoriza primero y lee o escribe después. Escribir, corregir y leer como mentor exigen acompañar HOY el grupo del aprendiz
 * y que el aprendiz sea de él: un mentor de otro grupo, o uno que ya fue rotado, recibe 403 {@code NO_ACOMPANA} y no ve ni una
 * nota. Las reglas de la nota (el texto, quién la corrige y cuándo) son del dominio ({@link NotaDelMentor}); acá solo se
 * resuelve la zona del autor y se arma la lista con los nombres.
 *
 * <p>El liderazgo lee por {@link NotasDelMentorFinder}: su guard (rol y formación) es de {@code leadership}.
 */
@Service
public class NotasDelMentorService implements EscribirNotaDelAprendizUseCase, CorregirNotaDelAprendizUseCase,
        ConsultarNotasDelAprendizUseCase, NotasDelMentorFinder {

    private final AccesoAVistasDelSemaforo acceso;
    private final GuardarNotaDelMentorPort guardar;
    private final LeerNotasDelMentorPort leer;
    private final ParticipacionProgramaFinder participaciones;
    private final UserSummaryFinder usuarios;
    private final IdGenerator idGenerator;
    private final Clock clock;

    NotasDelMentorService(AccesoAVistasDelSemaforo acceso, GuardarNotaDelMentorPort guardar,
                          LeerNotasDelMentorPort leer, ParticipacionProgramaFinder participaciones,
                          UserSummaryFinder usuarios, IdGenerator idGenerator, Clock clock) {
        this.acceso = acceso;
        this.guardar = guardar;
        this.leer = leer;
        this.participaciones = participaciones;
        this.usuarios = usuarios;
        this.idGenerator = idGenerator;
        this.clock = clock;
    }

    @Override
    @Transactional
    public NotaDelAprendiz escribir(EscribirNota pedido) {
        UserId aprendiz = exigirQueAcompana(pedido.actorId(), pedido.grupoId(), pedido.aprendizId());
        NotaDelMentor nota = NotaDelMentor.escribir(idGenerator.newId(), aprendiz, pedido.actorId(), pedido.texto(),
                zonaDe(pedido.actorId()), clock);
        guardar.insertar(nota);
        return vistas(List.of(nota), pedido.actorId()).getFirst();
    }

    @Override
    @Transactional
    public NotaDelAprendiz corregir(CorregirNota pedido) {
        UserId aprendiz = exigirQueAcompana(pedido.actorId(), pedido.grupoId(), pedido.aprendizId());
        NotaDelMentor nota = leer.porId(pedido.notaId()).filter(n -> n.aprendizId().equals(aprendiz))
                .orElseThrow(() -> new NoSuchElementException("Nota no encontrada"));
        VersionDeLaNota anterior = nota.corregir(pedido.actorId(), pedido.texto(), zonaDe(pedido.actorId()), clock);
        guardar.corregir(nota, anterior);
        return vistas(List.of(nota), pedido.actorId()).getFirst();
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotaDelAprendiz> notasDelMentor(ConsultaNotasDelAprendiz consulta) {
        UserId aprendiz = exigirQueAcompana(consulta.actorId(), consulta.grupoId(), consulta.aprendizId());
        return vistas(leer.delAprendiz(aprendiz), consulta.actorId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotaDelAprendiz> notasParaAdministracion(UserId actorId, UUID aprendizId) {
        acceso.requireAdminActivo(actorId);
        UserId aprendiz = UserId.of(aprendizId);
        if (usuarios.findById(aprendiz).isEmpty()) {
            throw new NoSuchElementException("Persona no encontrada");
        }
        return notasDe(aprendiz);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NotaDelAprendiz> notasDe(UserId aprendiz) {
        return vistas(leer.delAprendiz(aprendiz), null);
    }

    private UserId exigirQueAcompana(UserId actorId, UUID grupoId, UUID aprendizId) {
        Instant ahora = clock.now();
        UserId aprendiz = UserId.of(aprendizId);
        acceso.requireAcompananteVigente(actorId, grupoId, ahora);
        acceso.requireAprendizDelGrupo(aprendiz, grupoId, ahora);
        return aprendiz;
    }

    /** El «mismo día» de una nota es el del AUTOR, en su zona (regla 02): la de su programa; Lima si no tiene uno. */
    private ZoneId zonaDe(UserId autor) {
        return participaciones.deParticipante(autor).map(p -> p.zona()).orElse(ZoneId.of("America/Lima"));
    }

    /** Sin autor, la cuenta se dio de baja (el dueño, 17/11): {@code id} null y «Cuenta eliminada». */
    private static NotaDelAprendiz.Autor autorDe(NotaDelMentor nota, UserSummary autor) {
        if (nota.autorId() == null) {
            return new NotaDelAprendiz.Autor(null, "Cuenta eliminada");
        }
        return new NotaDelAprendiz.Autor(nota.autorId().value(), autor == null ? null : autor.fullName());
    }

    /** @param lector quien las pide para poder corregirlas; null = solo lee (liderazgo y Administración) */
    private List<NotaDelAprendiz> vistas(List<NotaDelMentor> notas, UserId lector) {
        Map<UserId, UserSummary> autores = usuarios.findByIds(notas.stream().map(NotaDelMentor::autorId).filter(java.util.Objects::nonNull).distinct().toList());
        ZoneId zonaDelLector = lector == null ? null : zonaDe(lector);
        return notas.stream().map(nota -> {
            UserSummary autor = nota.autorId() == null ? null : autores.get(nota.autorId());
            boolean puede = lector != null && nota.puedeCorregirla(lector, zonaDelLector, clock);
            return new NotaDelAprendiz(nota.id(), nota.texto(),
                    autorDe(nota, autor),
                    nota.creadoEn(), nota.corregidoEn(), puede);
        }).toList();
    }
}
