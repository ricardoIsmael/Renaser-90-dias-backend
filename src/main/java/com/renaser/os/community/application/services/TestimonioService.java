package com.renaser.os.community.application.services;

import com.renaser.os.community.application.ports.in.testimonio.ConsultarTestimoniosUseCase;
import com.renaser.os.community.application.ports.in.testimonio.CrearTestimonioUseCase;
import com.renaser.os.community.application.ports.in.testimonio.PromoverPublicacionATestimonioUseCase;
import com.renaser.os.community.application.ports.out.publicacion.LoadPublicacionPort;
import com.renaser.os.community.application.ports.out.testimonio.LoadTestimonioPort;
import com.renaser.os.community.application.ports.out.testimonio.SaveTestimonioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort.PerfilUsuario;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.testimonio.Testimonio;
import com.renaser.os.community.domain.model.testimonio.TestimonioId;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

@Service
public class TestimonioService implements CrearTestimonioUseCase, PromoverPublicacionATestimonioUseCase,
        ConsultarTestimoniosUseCase {

    private static final int LIMITE_LISTADO = 50;
    private static final Duration VALIDEZ_URL_LECTURA = Duration.ofMinutes(15);

    private final LoadTestimonioPort loadTestimonioPort;
    private final SaveTestimonioPort saveTestimonioPort;
    private final LoadPublicacionPort loadPublicacionPort;
    private final ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    private final AlmacenamientoPort almacenamientoPort;
    private final UserSummaryFinder userSummaryFinder;
    private final Clock clock;
    private final IdGenerator idGenerator;

    public TestimonioService(LoadTestimonioPort loadTestimonioPort, SaveTestimonioPort saveTestimonioPort,
                              LoadPublicacionPort loadPublicacionPort,
                              ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort,
                              AlmacenamientoPort almacenamientoPort, UserSummaryFinder userSummaryFinder,
                              Clock clock, IdGenerator idGenerator) {
        this.loadTestimonioPort = loadTestimonioPort;
        this.saveTestimonioPort = saveTestimonioPort;
        this.loadPublicacionPort = loadPublicacionPort;
        this.consultarPerfilUsuarioPort = consultarPerfilUsuarioPort;
        this.almacenamientoPort = almacenamientoPort;
        this.userSummaryFinder = userSummaryFinder;
        this.clock = clock;
        this.idGenerator = idGenerator;
    }

    @Override
    @Transactional
    public TestimonioVista crear(CrearTestimonioCommand command) {
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
        Testimonio testimonio = Testimonio.crear(TestimonioId.of(idGenerator.newId()), command.actorId(), null,
                command.nombre(), command.rolTexto(), null, null, command.texto(), command.estrellas(), clock.now());
        return aVista(saveTestimonioPort.save(testimonio));
    }

    @Override
    @Transactional
    public TestimonioVista promover(PromoverPublicacionCommand command) {
        requireAdmin(command.actorId());
        Publicacion publicacion = loadPublicacionPort.porId(command.publicacionId())
                .orElseThrow(() -> new NoSuchElementException("Publicacion no encontrada: " + command.publicacionId()));
        PerfilUsuario autor = consultarPerfilUsuarioPort.porId(publicacion.autorId())
                .orElseThrow(() -> new NoSuchElementException("Autor no encontrado: " + publicacion.autorId()));
        UserSummary resumenAutor = userSummaryFinder.findById(publicacion.autorId())
                .orElseThrow(() -> new NoSuchElementException("Autor no encontrado: " + publicacion.autorId()));
        // La portada es la primera foto del carrusel — la publicacion siempre tiene al
        // menos una (invariante de Publicacion.publicar), asi que no hace falta fallback.
        Optional<MediaPublicacion> portada = publicacion.media().stream()
                .min((a, b) -> Integer.compare(a.orden(), b.orden()));
        String fotoEventoRuta = portada.map(MediaPublicacion::ruta).orElse(null);

        // El avatar se congela a proposito: un testimonio es una foto de un momento. Vale ahora
        // que `usuarios.avatar_url` guarda una URL PERMANENTE; cuando guardaba una prefirmada,
        // esta copia heredaba el vencimiento y quedaba rota igual (E-57 — la migracion V13
        // limpia las filas que quedaron con una firma congelada aca).
        // La identidad entra por el puerto IdGenerator, no la sortea el agregado (CLAUDE.MD sec. 5.4.7).
        Testimonio testimonio = Testimonio.crear(TestimonioId.of(idGenerator.newId()), publicacion.autorId(),
                publicacion.id(), autor.nombreCompleto(), resumenAutor.role().name(), autor.avatarUrl(),
                fotoEventoRuta, publicacion.texto(), command.estrellas(), clock.now());
        return aVista(saveTestimonioPort.save(testimonio));
    }

    @Override
    public List<TestimonioVista> listarDestacados() {
        return loadTestimonioPort.listarDestacados(LIMITE_LISTADO).stream().map(this::aVista).toList();
    }

    private TestimonioVista aVista(Testimonio testimonio) {
        String avatar = testimonio.avatarUrl();
        if ((avatar == null || avatar.isBlank()) && testimonio.usuarioId() != null) {
            avatar = consultarPerfilUsuarioPort.porId(testimonio.usuarioId()).map(PerfilUsuario::avatarUrl)
                    .orElse(null);
        }
        URI fotoEvento = (testimonio.fotoEventoRuta() == null || testimonio.fotoEventoRuta().isBlank())
                ? null
                : almacenamientoPort.firmarLectura(testimonio.fotoEventoRuta(), VALIDEZ_URL_LECTURA);
        return new TestimonioVista(testimonio, avatar, fotoEvento);
    }

    private void requireAdmin(UserId actorId) {
        UserSummary actor = userSummaryFinder.findById(actorId)
                .orElseThrow(() -> new NoSuchElementException("Actor no encontrado: " + actorId));
        if (actor.status() != UserStatus.ACTIVE) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        if (actor.role() != UserRole.ADMIN && actor.role() != UserRole.ALCHEMIST) {
            throw new NotAuthorizedException("Solo administradores pueden promover publicaciones");
        }
    }
}
