package com.renaser.os.community.application.services;

import com.renaser.os.community.api.PublicacionCreadaEvent;
import com.renaser.os.community.api.PublicarEnMuroPort.PublicarDesdeEvidenciaComando;
import com.renaser.os.community.application.ports.in.categoria.ConsultarCategoriasMuroUseCase;
import com.renaser.os.community.application.ports.in.publicacion.EditarPublicacionUseCase.EditarPublicacionCommand;
import com.renaser.os.community.application.ports.in.publicacion.OcultarPublicacionUseCase.OcultarPublicacionCommand;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase.ArchivoEntrada;
import com.renaser.os.community.application.ports.in.publicacion.PublicarUseCase.PublicarCommand;
import com.renaser.os.community.application.ports.in.publicacion.EliminarPublicacionUseCase.EliminarPublicacionCommand;
import com.renaser.os.community.application.ports.in.publicacion.ReaccionarUseCase.ReaccionarCommand;
import com.renaser.os.community.application.ports.in.publicacion.RestaurarPublicacionUseCase.RestaurarPublicacionCommand;
import com.renaser.os.community.application.ports.in.publicacion.SolicitarUrlSubidaMediaUseCase.SolicitarUrlSubidaMediaCommand;
import com.renaser.os.community.application.ports.out.publicacion.EliminarPublicacionPort;
import com.renaser.os.community.application.ports.out.publicacion.LoadComentarioPort;
import com.renaser.os.community.application.ports.out.publicacion.LoadPublicacionPort;
import com.renaser.os.community.application.ports.out.publicacion.ReaccionMuroPort;
import com.renaser.os.community.application.ports.out.publicacion.SavePublicacionPort;
import com.renaser.os.community.application.ports.out.usuario.ConsultarPerfilUsuarioPort;
import com.renaser.os.community.domain.model.publicacion.MediaPublicacion;
import com.renaser.os.community.domain.model.publicacion.Publicacion;
import com.renaser.os.community.domain.model.publicacion.PublicacionId;
import com.renaser.os.community.domain.model.publicacion.ReaccionMuro;
import com.renaser.os.community.domain.model.publicacion.TipoPublicacion;
import com.renaser.os.community.domain.model.publicacion.TipoReaccion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.infrastructure.storage.NoOpAlmacenamientoAdapter;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicacionMuroServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, la factoria del agregado ya no lo sortea. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadPublicacionPort loadPublicacionPort;
    @Mock
    private SavePublicacionPort savePublicacionPort;
    @Mock
    private EliminarPublicacionPort eliminarPublicacionPort;
    @Mock
    private LoadComentarioPort loadComentarioPort;
    @Mock
    private ReaccionMuroPort reaccionMuroPort;
    @Mock
    private ConsultarCategoriasMuroUseCase categoriasUseCase;
    @Mock
    private ConsultarPerfilUsuarioPort consultarPerfilUsuarioPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private ApplicationEventPublisher events;
    @Mock
    private IdGenerator idGenerator;

    private PublicacionMuroService service;

    private final UserId autor = UserId.of(UUID.randomUUID());
    private final UserId otro = UserId.of(UUID.randomUUID());
    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId suspendido = UserId.of(UUID.randomUUID());
    private final UserId adminSuspendido = UserId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new PublicacionMuroService(loadPublicacionPort, savePublicacionPort, eliminarPublicacionPort,
                loadComentarioPort, reaccionMuroPort, categoriasUseCase, consultarPerfilUsuarioPort,
                new NoOpAlmacenamientoAdapter(), userSummaryFinder, events, CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(userSummaryFinder.findById(autor))
                .thenReturn(Optional.of(new UserSummary(autor, "Autor", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(otro))
                .thenReturn(Optional.of(new UserSummary(otro, "Otro", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendido)).thenReturn(
                Optional.of(new UserSummary(suspendido, "Suspendido", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));
        lenient().when(userSummaryFinder.findById(adminSuspendido)).thenReturn(Optional.of(new UserSummary(
                adminSuspendido, "Admin suspendido", null, UserRole.ADMIN, UserStatus.SUSPENDED)));
        // La proyeccion del feed va SIEMPRE por los metodos en lote (E-80). Los hermanos de a una
        // (`porId`/`contarPorTipo`/`contar`) siguen existiendo para otros usos, pero esta clase ya
        // no debe llamarlos al armar una vista — de eso se ocupa
        // `laProyeccionNoConsultaUnaVezPorPublicacion`.
        lenient().when(consultarPerfilUsuarioPort.porIds(any())).thenReturn(Map.of());
        lenient().when(reaccionMuroPort.contarPorTipoDeVarias(any())).thenReturn(Map.of());
        lenient().when(reaccionMuroPort.deUsuarioEnVarias(any(), any())).thenReturn(Map.of());
        lenient().when(loadComentarioPort.contarDeVarias(any())).thenReturn(Map.of());
        lenient().when(savePublicacionPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private static List<ArchivoEntrada> unaFoto() {
        return List.of(new ArchivoEntrada("wall", "muro/x/1.jpg", "image/jpeg"));
    }

    private Publicacion publicacionVisible(UserId autorId) {
        return Publicacion.rehydrate(PublicacionId.of(UUID.randomUUID()), autorId,
                com.renaser.os.community.domain.model.publicacion.TipoPublicacion.MANUAL, null, "hola",
                List.of(new MediaPublicacion("wall", "muro/x/1.jpg", "image/jpeg", 0)), false, CLOCK.now(),
                CLOCK.now());
    }

    /**
     * Fija E-80. La prueba mira la <b>propiedad</b> — "el costo no depende del tamano de la
     * pagina" — y no un numero de consultas concreto: sigue matando el defecto aunque manana se
     * agregue otro dato a la vista, mientras ese dato tambien se pida en lote.
     *
     * <p>Un `verify(..., times(20))` seria la prueba equivocada: pasaria en verde justo cuando el
     * defecto vuelve. Por eso se verifica que los metodos de a UNA <b>nunca</b> se llamen.
     */
    @Test
    @DisplayName("feed(): la proyeccion no consulta una vez por publicacion, por grande que sea la pagina")
    void laProyeccionNoConsultaUnaVezPorPublicacion() {
        List<Publicacion> veinte = java.util.stream.IntStream.range(0, 20)
                .mapToObj(i -> publicacionVisible(autor)).toList();
        when(loadPublicacionPort.feed(any(), anyInt(), any())).thenReturn(veinte);

        var pagina = service.feed(autor, null, null);

        assertThat(pagina.publicaciones()).hasSize(20);
        verify(consultarPerfilUsuarioPort, never()).porId(any());
        verify(reaccionMuroPort, never()).contarPorTipo(any());
        verify(reaccionMuroPort, never()).deUsuario(any(), any());
        verify(loadComentarioPort, never()).contar(any());
        // Una sola pasada por cada dato, sin importar que sean 20 publicaciones.
        verify(consultarPerfilUsuarioPort, times(1)).porIds(any());
        verify(reaccionMuroPort, times(1)).contarPorTipoDeVarias(any());
        verify(reaccionMuroPort, times(1)).deUsuarioEnVarias(any(), any());
        verify(loadComentarioPort, times(1)).contarDeVarias(any());
    }

    @Test
    @DisplayName("feed() vacio: no se consulta nada en lote — un IN () vacio no es SQL valido")
    void feedVacioNoConsultaEnLote() {
        when(loadPublicacionPort.feed(any(), anyInt(), any())).thenReturn(List.of());

        assertThat(service.feed(autor, null, null).publicaciones()).isEmpty();

        verify(reaccionMuroPort, never()).contarPorTipoDeVarias(any());
        verify(loadComentarioPort, never()).contarDeVarias(any());
    }

    @Test
    void publicarConCategoriaDesconocidaFalla() {
        when(categoriasUseCase.clavesExistentes()).thenReturn(Set.of("LOGROS"));
        var command = new PublicarCommand(autor, "hola comunidad", unaFoto(), "INEXISTENTE");
        assertThatThrownBy(() -> service.publicar(command)).isInstanceOf(IllegalArgumentException.class);
        verify(savePublicacionPort, never()).save(any());
    }

    @Test
    void publicarPublicaElEventoDeDominio() {
        var command = new PublicarCommand(autor, "hola comunidad", unaFoto(), null);
        service.publicar(command);
        verify(events, times(1)).publishEvent(any(PublicacionCreadaEvent.class));
    }

    @Test
    void ocultarUnaAjenaSinModerarFalla() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));
        var command = new OcultarPublicacionCommand(otro, publicacion.id());
        assertThatThrownBy(() -> service.ocultar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePublicacionPort, never()).save(any());
    }

    /** Regresion de seguridad (mismo criterio que ComentarioMuroServiceTest): un actor
     * que no existe en `usuarios` intentando moderar debe recibir 403, nunca 404 — un 404
     * en este punto delataria que la publicacion SI existe (docs/MODULO_COMMUNITY.md sec. 5). */
    @Test
    void ocultarConActorInexistenteEsRechazadoComo403NoComo404() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));
        UserId fantasma = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(fantasma)).thenReturn(Optional.empty());

        var command = new OcultarPublicacionCommand(fantasma, publicacion.id());
        assertThatThrownBy(() -> service.ocultar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePublicacionPort, never()).save(any());
    }

    @Test
    void ocultarLaPropiaFunciona() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));
        service.ocultar(new OcultarPublicacionCommand(autor, publicacion.id()));
        assertThat(publicacion.oculta()).isTrue();
    }

    @Test
    void moderadorPuedeOcultarUnaAjena() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));
        service.ocultar(new OcultarPublicacionCommand(admin, publicacion.id()));
        assertThat(publicacion.oculta()).isTrue();
    }

    @Test
    void reaccionarConElMismoTipoLaQuita() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));
        when(reaccionMuroPort.deUsuario(publicacion.id(), otro)).thenReturn(Optional.of(TipoReaccion.ME_GUSTA));

        var resultado = service.reaccionar(new ReaccionarCommand(otro, publicacion.id(), TipoReaccion.ME_GUSTA));

        assertThat(resultado.reaccionado()).isFalse();
        verify(reaccionMuroPort).eliminar(publicacion.id(), otro);
        verify(reaccionMuroPort, never()).upsert(any(), any(), any());
    }

    @Test
    void reaccionarConOtroTipoLoReemplaza() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));
        when(reaccionMuroPort.deUsuario(publicacion.id(), otro)).thenReturn(Optional.of(TipoReaccion.ME_GUSTA));

        var resultado = service.reaccionar(new ReaccionarCommand(otro, publicacion.id(), TipoReaccion.NO_ME_GUSTA));

        assertThat(resultado.reaccionado()).isTrue();
        verify(reaccionMuroPort).upsert(publicacion.id(), otro, TipoReaccion.NO_ME_GUSTA);
    }

    /** Regresion (auditoria E2E adversarial): reaccionar/editar/ocultar no chequeaban el
     * estado del actor en absoluto — a diferencia de publicar/feed, que si lo hacian. */
    @Test
    void reaccionarConActorSuspendidoFalla() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));

        var command = new ReaccionarCommand(suspendido, publicacion.id(), TipoReaccion.ME_GUSTA);
        assertThatThrownBy(() -> service.reaccionar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(reaccionMuroPort, never()).upsert(any(), any(), any());
    }

    @Test
    void editarConActorSuspendidoFalla() {
        Publicacion publicacion = publicacionVisible(suspendido);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));

        var command = new EditarPublicacionCommand(suspendido, publicacion.id(), "nuevo texto", unaFoto());
        assertThatThrownBy(() -> service.editar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePublicacionPort, never()).save(any());
    }

    @Test
    void ocultarConActorSuspendidoFalla() {
        Publicacion publicacion = publicacionVisible(suspendido);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));

        var command = new OcultarPublicacionCommand(suspendido, publicacion.id());
        assertThatThrownBy(() -> service.ocultar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePublicacionPort, never()).save(any());
    }

    /** Hueco #17 (docs/MODULO_ROCKS.md sec. 11.2): puerto publico para que otro modulo
     * (rocks) publique en el Muro desde una evidencia ya subida. */
    @Test
    void publicarDesdeEvidenciaCreaUnaPublicacionHitoAutomatico() {
        var comando = new PublicarDesdeEvidenciaComando(autor, "Completo mi Roca: Meditar", "renaser-files",
                "rocas/x/y", "image/jpeg");

        UUID id = service.publicarDesdeEvidencia(comando);

        assertThat(id).isNotNull();
        verify(savePublicacionPort).save(argThat(p -> p.tipo() == TipoPublicacion.HITO_AUTOMATICO
                && p.autorId().equals(autor) && p.media().size() == 1
                && p.media().get(0).bucket().equals("renaser-files") && p.media().get(0).ruta().equals("rocas/x/y")
                && p.categoriaClave() == null));
        verify(events).publishEvent(any(PublicacionCreadaEvent.class));
    }

    @Test
    void publicarDesdeEvidenciaConActorSuspendidoFalla() {
        var comando = new PublicarDesdeEvidenciaComando(suspendido, "texto", "renaser-files", "rocas/x/y",
                "image/jpeg");

        assertThatThrownBy(() -> service.publicarDesdeEvidencia(comando)).isInstanceOf(NotAuthorizedException.class);
        verify(savePublicacionPort, never()).save(any());
    }

    // ─── reacciones(): quien reacciono al post (modal "Reacciones del post") ──────────────

    @Test
    @DisplayName("reacciones(): resuelve nombre/avatar/rol de cada reactor en UNA sola consulta en lote, nunca una por reactor")
    void reaccionesResuelveDatosDeUsuarioEnLote() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));
        when(reaccionMuroPort.listarDe(publicacion.id())).thenReturn(List.of(
                new ReaccionMuro(publicacion.id(), otro, TipoReaccion.ME_GUSTA),
                new ReaccionMuro(publicacion.id(), admin, TipoReaccion.NO_ME_GUSTA)));
        // Se compara el CONTENIDO, no el tipo de coleccion ni el orden. El puerto recibe un
        // Collection y el servicio le pasa una List armada con un stream: ese orden es incidental,
        // y un Set nunca es igual a una List, asi que fijar cualquiera de los dos aca hace fallar
        // el test por un detalle que no es una regla de negocio.
        when(userSummaryFinder.findByIds(argThat(ids -> ids != null && Set.copyOf(ids).equals(Set.of(otro, admin)))))
                .thenReturn(Map.of(
                        otro, new UserSummary(otro, "Otro", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                        admin, new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));

        var vistas = service.reacciones(autor, publicacion.id());

        assertThat(vistas).hasSize(2);
        assertThat(vistas).anySatisfy(v -> {
            assertThat(v.usuarioId()).isEqualTo(otro);
            assertThat(v.nombre()).isEqualTo("Otro");
            assertThat(v.rol()).isEqualTo(UserRole.TRAINEE);
            assertThat(v.tipo()).isEqualTo(TipoReaccion.ME_GUSTA);
        });
        verify(userSummaryFinder, times(1)).findByIds(any());
        verify(userSummaryFinder, never()).findById(otro);
        verify(userSummaryFinder, never()).findById(admin);
    }

    @Test
    @DisplayName("reacciones(): publicacion sin ninguna reaccion devuelve lista vacia, no consulta usuarios")
    void reaccionesDePublicacionSinReaccionesEsVacia() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));
        when(reaccionMuroPort.listarDe(publicacion.id())).thenReturn(List.of());

        assertThat(service.reacciones(autor, publicacion.id())).isEmpty();
        verify(userSummaryFinder, never()).findByIds(any());
    }

    @Test
    @DisplayName("reacciones(): publicacion oculta o inexistente -> 404, no 403")
    void reaccionesDePublicacionNoVisibleEsNoEncontrada() {
        UUID idInexistente = UUID.randomUUID();
        when(loadPublicacionPort.porId(PublicacionId.of(idInexistente))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reacciones(autor, PublicacionId.of(idInexistente)))
                .isInstanceOf(java.util.NoSuchElementException.class);
    }

    @Test
    @DisplayName("reacciones(): cuenta SUSPENDIDA -> 403, nunca lista quien reacciono")
    void reaccionesConActorSuspendidoFalla() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));

        assertThatThrownBy(() -> service.reacciones(suspendido, publicacion.id()))
                .isInstanceOf(NotAuthorizedException.class);
        verify(reaccionMuroPort, never()).listarDe(any());
    }

    @Test
    @DisplayName("reacciones(): actor inexistente -> 403, no 404 (no delata si la publicacion existe)")
    void reaccionesConActorInexistenteEsRechazadoComo403NoComo404() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));
        UserId fantasma = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.reacciones(fantasma, publicacion.id()))
                .isInstanceOf(NotAuthorizedException.class);
        verify(reaccionMuroPort, never()).listarDe(any());
    }

    // ─── CLAUDE.MD sec. 0.3: 403 por rol y 403 por cuenta SUSPENDIDA, metodo por metodo ──
    // Estaban probados ocultar/editar/reaccionar/publicarDesdeEvidencia; faltaban sus
    // hermanos publicar/feed/feedOculto/restaurar/eliminarPermanente/solicitarUrl y los dos
    // metodos que directamente no tenian guard (ultimoAutor/contarMisPublicaciones, E-50).

    @Test
    @DisplayName("publicar(): cuenta SUSPENDIDA -> 403, nunca guarda")
    void publicarConActorSuspendidoFalla() {
        var command = new PublicarCommand(suspendido, "hola comunidad", unaFoto(), null);
        assertThatThrownBy(() -> service.publicar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePublicacionPort, never()).save(any());
    }

    @Test
    @DisplayName("feed(): cuenta SUSPENDIDA -> 403, nunca consulta el feed")
    void feedConActorSuspendidoFalla() {
        assertThatThrownBy(() -> service.feed(suspendido, null, null)).isInstanceOf(NotAuthorizedException.class);
        verify(loadPublicacionPort, never()).feed(any(), anyInt(), any());
    }

    @Test
    @DisplayName("feedOculto(): rol sin permiso (TRAINEE) -> 403 — la cola de moderacion es ADMIN/ALCHEMIST")
    void feedOcultoComoNoModeradorEsRechazado() {
        assertThatThrownBy(() -> service.feedOculto(otro, null)).isInstanceOf(NotAuthorizedException.class);
        verify(loadPublicacionPort, never()).feedOculto(any(), anyInt());
    }

    @Test
    @DisplayName("feedOculto(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void feedOcultoConAdminSuspendidoFalla() {
        assertThatThrownBy(() -> service.feedOculto(adminSuspendido, null))
                .isInstanceOf(NotAuthorizedException.class);
        verify(loadPublicacionPort, never()).feedOculto(any(), anyInt());
    }

    @Test
    @DisplayName("restaurar(): rol sin permiso (TRAINEE) -> 403, nunca guarda")
    void restaurarComoNoModeradorEsRechazado() {
        var command = new RestaurarPublicacionCommand(otro, PublicacionId.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.restaurar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePublicacionPort, never()).save(any());
    }

    @Test
    @DisplayName("restaurar(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void restaurarConAdminSuspendidoFalla() {
        var command = new RestaurarPublicacionCommand(adminSuspendido, PublicacionId.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.restaurar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(savePublicacionPort, never()).save(any());
    }

    @Test
    @DisplayName("eliminarPermanente(): rol sin permiso (TRAINEE) -> 403, nunca borra")
    void eliminarPermanenteComoNoModeradorEsRechazado() {
        var command = new EliminarPublicacionCommand(otro, PublicacionId.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.eliminarPermanente(command)).isInstanceOf(NotAuthorizedException.class);
        verify(eliminarPublicacionPort, never()).eliminar(any());
    }

    @Test
    @DisplayName("eliminarPermanente(): cuenta SUSPENDIDA -> 403 aunque el rol sea ADMIN")
    void eliminarPermanenteConAdminSuspendidoFalla() {
        var command = new EliminarPublicacionCommand(adminSuspendido, PublicacionId.of(UUID.randomUUID()));
        assertThatThrownBy(() -> service.eliminarPermanente(command)).isInstanceOf(NotAuthorizedException.class);
        verify(eliminarPublicacionPort, never()).eliminar(any());
    }

    @Test
    @DisplayName("ocultar(): un moderador SUSPENDIDO no modera contenido ajeno -> 403")
    void moderadorSuspendidoNoPuedeOcultarUnaAjena() {
        Publicacion publicacion = publicacionVisible(autor);
        when(loadPublicacionPort.porId(publicacion.id())).thenReturn(Optional.of(publicacion));

        var command = new OcultarPublicacionCommand(adminSuspendido, publicacion.id());
        assertThatThrownBy(() -> service.ocultar(command)).isInstanceOf(NotAuthorizedException.class);
        assertThat(publicacion.oculta()).isFalse();
        verify(savePublicacionPort, never()).save(any());
    }

    @Test
    @DisplayName("solicitarUrl(): cuenta SUSPENDIDA -> 403")
    void solicitarUrlConActorSuspendidoFalla() {
        var command = new SolicitarUrlSubidaMediaCommand(suspendido, "image/jpeg");
        assertThatThrownBy(() -> service.solicitarUrl(command)).isInstanceOf(NotAuthorizedException.class);
    }

    // ─── Prefijos separados por tipo de archivo dentro de muro/ ───────────────────────────
    // No se afirma la ruta completa: el UUID final es aleatorio y afirmarlo entero volveria
    // la prueba fragil sin verificar nada mas. Lo que importa es en que carpeta cae.

    @Test
    @DisplayName("solicitarUrl(): una imagen va al prefijo muro/fotos/")
    void solicitarUrlDeImagenVaAlPrefijoDeFotos() {
        var command = new SolicitarUrlSubidaMediaCommand(autor, "image/jpeg");
        assertThat(service.solicitarUrl(command).ruta()).startsWith("muro/fotos/" + autor);
    }

    @Test
    @DisplayName("solicitarUrl(): un video va al prefijo muro/videos/")
    void solicitarUrlDeVideoVaAlPrefijoDeVideos() {
        var command = new SolicitarUrlSubidaMediaCommand(autor, "video/mp4");
        assertThat(service.solicitarUrl(command).ruta()).startsWith("muro/videos/" + autor);
    }

    @Test
    @DisplayName("solicitarUrl(): un tipo que no es imagen ni video se rechaza ANTES de firmar")
    void solicitarUrlRechazaUnTipoQueNoEsImagenNiVideo() {
        // Firmar una subida que el dominio va a rechazar despues deja un objeto huerfano
        // en el bucket: por eso el corte tiene que ocurrir en este punto, no mas adelante.
        var command = new SolicitarUrlSubidaMediaCommand(autor, "application/pdf");
        assertThatThrownBy(() -> service.solicitarUrl(command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("image/ o video/");
    }

    /** E-50: `ultimoAutor` devolvia el nombre completo del ultimo autor del Muro SIN
     * ningun guard — el controller ya recibia el actor y no lo usaba. */
    @Test
    @DisplayName("ultimoAutor(): cuenta SUSPENDIDA -> 403, no filtra el nombre del ultimo autor")
    void ultimoAutorConActorSuspendidoFalla() {
        assertThatThrownBy(() -> service.ultimoAutor(suspendido)).isInstanceOf(NotAuthorizedException.class);
        verify(loadPublicacionPort, never()).ultimaVisible();
    }

    @Test
    @DisplayName("contarMisPublicaciones(): cuenta SUSPENDIDA -> 403")
    void contarMisPublicacionesConActorSuspendidoFalla() {
        assertThatThrownBy(() -> service.contarMisPublicaciones(suspendido))
                .isInstanceOf(NotAuthorizedException.class);
        verify(loadPublicacionPort, never()).contarPorAutor(any());
    }
}
