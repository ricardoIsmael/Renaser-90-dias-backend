package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.guiaadmin.ConfirmarAdjuntoGuiaArchivoUseCase.ConfirmarAdjuntoGuiaArchivoCommand;
import com.renaser.os.habits.application.ports.in.guiaadmin.CrearAdjuntoGuiaEnlaceUseCase.CrearAdjuntoGuiaEnlaceCommand;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarAdjuntoGuiaUseCase.EliminarAdjuntoGuiaCommand;
import com.renaser.os.habits.application.ports.in.guiaadmin.EliminarGuiaHabitoUseCase.EliminarGuiaHabitoCommand;
import com.renaser.os.habits.application.ports.in.guiaadmin.SolicitarUrlAdjuntoGuiaUseCase.SolicitarUrlAdjuntoGuiaCommand;
import com.renaser.os.habits.application.ports.in.guiaadmin.UpsertGuiaHabitoUseCase.UpsertGuiaHabitoCommand;
import com.renaser.os.habits.application.ports.out.adjunto.LoadAdjuntoGuiaPort;
import com.renaser.os.habits.application.ports.out.adjunto.SaveAdjuntoGuiaPort;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.guia.SaveGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuia;
import com.renaser.os.habits.domain.model.guia.AdjuntoGuiaId;
import com.renaser.os.habits.domain.model.guia.ContenidoGuia;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.guia.SeccionGuia;
import com.renaser.os.habits.domain.model.guia.TipoMedioGuia;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuiaHabitoAdminServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-26T10:00:00Z"));
    /** Identidad fija: con el id entrando por el puerto IdGenerator, las factorias ya no lo sortean. */
    private static final UUID ID_GENERADO = UUID.fromString("00000000-0000-4000-8000-000000000001");

    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private LoadGuiaHabitoPort loadGuiaPort;
    @Mock
    private SaveGuiaHabitoPort saveGuiaPort;
    @Mock
    private LoadAdjuntoGuiaPort loadAdjuntoPort;
    @Mock
    private SaveAdjuntoGuiaPort saveAdjuntoPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private IdGenerator idGenerator;

    private GuiaHabitoAdminService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private final UserId suspendedAdmin = UserId.of(UUID.randomUUID());
    private final HabitoId habitoId = HabitoId.of(UUID.randomUUID());

    @BeforeEach
    void setUp() {
        service = new GuiaHabitoAdminService(loadHabitoPort, loadGuiaPort, saveGuiaPort, loadAdjuntoPort,
                saveAdjuntoPort, new HabitoAdminGuard(userSummaryFinder), almacenamientoPort, CLOCK, idGenerator);
        lenient().when(idGenerator.newId()).thenReturn(ID_GENERADO);
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(mentor))
                .thenReturn(Optional.of(new UserSummary(mentor, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(suspendedAdmin)).thenReturn(Optional.of(
                new UserSummary(suspendedAdmin, "Admin suspendido", null, UserRole.ADMIN, UserStatus.SUSPENDED)));
        // El habito que devuelve el mock lleva el mismo id que se consulta, como pasaria contra
        // el adaptador de persistencia real: ahora que el id entra por parametro se puede fijar.
        lenient().when(loadHabitoPort.byId(habitoId)).thenReturn(Optional.of(Habito.crearDeSistema(habitoId, "Titulo",
                TipoHabito.CHECKBOX, new DetallesHabito(null, "CUERPO", ExigenciaEvidencia.OPCIONAL, false, false),
                CLOCK.now())));
        lenient().when(saveGuiaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(saveAdjuntoPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(loadAdjuntoPort.porGuias(any())).thenReturn(List.of());
    }

    private static ContenidoGuia contenido() {
        return new ContenidoGuia("hacer", "como", null, null, null, null, null, null, null, null);
    }

    @Test
    void upsertComoMentorEsRechazado() {
        var command = new UpsertGuiaHabitoCommand(mentor, habitoId, 1, null, contenido(), false);
        assertThatThrownBy(() -> service.upsert(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveGuiaPort, never()).save(any());
    }

    @Test
    void upsertSinGuiaExistenteCreaUnaNueva() {
        when(loadGuiaPort.porHabito(habitoId)).thenReturn(List.of());
        var command = new UpsertGuiaHabitoCommand(admin, habitoId, 5, null, contenido(), false);

        var resultado = service.upsert(command);

        assertThat(resultado.guia().diaInicio()).isEqualTo(5);
        assertThat(resultado.guia().queHacer()).isEqualTo("hacer");
        assertThat(resultado.adjuntos()).isEmpty();
    }

    @Test
    void upsertConGuiaExistenteEnEseDiaLaEdita() {
        GuiaHabito existente = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 5, CLOCK.now());
        when(loadGuiaPort.porHabito(habitoId)).thenReturn(List.of(existente));
        var command = new UpsertGuiaHabitoCommand(admin, habitoId, 5, 20, contenido(), false);

        var resultado = service.upsert(command);

        assertThat(resultado.guia().id()).isEqualTo(existente.id());
        assertThat(resultado.guia().queHacer()).isEqualTo("hacer");
        assertThat(resultado.guia().diaFin()).isEqualTo(20);
    }

    @Test
    void upsertConClosePreviousCierraLaGuiaAbiertaAnterior() {
        GuiaHabito previa = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 1, CLOCK.now());
        when(loadGuiaPort.masRecienteAbierta(habitoId)).thenReturn(Optional.of(previa));
        when(loadGuiaPort.porHabito(habitoId)).thenReturn(List.of(previa));
        var command = new UpsertGuiaHabitoCommand(admin, habitoId, 10, null, contenido(), true);

        service.upsert(command);

        assertThat(previa.diaFin()).isEqualTo(9);
        verify(saveGuiaPort, times(2)).save(any()); // la previa cerrada + la nueva
    }

    @Test
    void upsertConClosePreviousNoSeCierraASiMisma() {
        GuiaHabito existente = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 10, CLOCK.now());
        when(loadGuiaPort.masRecienteAbierta(habitoId)).thenReturn(Optional.of(existente));
        when(loadGuiaPort.porHabito(habitoId)).thenReturn(List.of(existente));
        var command = new UpsertGuiaHabitoCommand(admin, habitoId, 10, null, contenido(), true);

        service.upsert(command);

        assertThat(existente.diaFin()).isNull();
        verify(saveGuiaPort, times(1)).save(any());
    }

    @Test
    void eliminarGuiaInexistenteFalla404() {
        GuiaHabitoId id = GuiaHabitoId.of(UUID.randomUUID());
        when(loadGuiaPort.byId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(new EliminarGuiaHabitoCommand(admin, id)))
                .isInstanceOf(NoSuchElementException.class);
        verify(saveGuiaPort, never()).eliminar(any());
    }

    @Test
    void crearAdjuntoEnlaceCreaLaGuiaSiNoExiste() {
        when(loadGuiaPort.porHabito(habitoId)).thenReturn(List.of());
        when(saveGuiaPort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        var command = new CrearAdjuntoGuiaEnlaceCommand(admin, habitoId, 3, SeccionGuia.QUE_HACER,
                "https://youtube.com/x", "Titulo");

        AdjuntoGuia adjunto = service.crear(command);

        assertThat(adjunto.seccion()).isEqualTo(SeccionGuia.QUE_HACER);
        assertThat(adjunto.url()).isEqualTo("https://youtube.com/x");
        assertThat(adjunto.orden()).isEqualTo(0);
        verify(saveGuiaPort).save(any());
    }

    @Test
    void crearAdjuntoEnlaceUsaLaGuiaExistenteYSiguienteOrden() {
        GuiaHabito existente = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 3, CLOCK.now());
        when(loadGuiaPort.porHabito(habitoId)).thenReturn(List.of(existente));
        when(loadAdjuntoPort.porGuias(List.of(existente.id()))).thenReturn(
                List.of(AdjuntoGuia.deEnlace(AdjuntoGuiaId.of(UUID.randomUUID()), existente.id(), SeccionGuia.QUE_HACER,
                        "https://a", null, 0, CLOCK.now())));
        var command = new CrearAdjuntoGuiaEnlaceCommand(admin, habitoId, 3, SeccionGuia.CIENCIA, "https://b", null);

        AdjuntoGuia adjunto = service.crear(command);

        assertThat(adjunto.orden()).isEqualTo(1);
        verify(saveGuiaPort, never()).save(any());
    }

    @Test
    void eliminarAdjuntoInexistenteFalla404() {
        AdjuntoGuiaId id = AdjuntoGuiaId.of(UUID.randomUUID());
        when(loadAdjuntoPort.byId(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.eliminar(new EliminarAdjuntoGuiaCommand(admin, id)))
                .isInstanceOf(NoSuchElementException.class);
        verify(saveAdjuntoPort, never()).eliminar(any());
    }

    @Test
    void solicitarUrlAdjuntoDevuelveBucketYRutaBajoElHabito() {
        when(almacenamientoPort.firmarSubida(anyString(), anyString(), any(Duration.class)))
                .thenReturn(URI.create("https://s3.example/guias-habito/" + habitoId));

        var resultado = service.solicitarUrl(new SolicitarUrlAdjuntoGuiaCommand(admin, habitoId, "image/png"));

        assertThat(resultado.bucket()).isEqualTo(GuiaHabitoAdminService.BUCKET_ADJUNTOS_GUIA);
        assertThat(resultado.ruta()).startsWith("guias-habito/" + habitoId + "/");
    }

    @Test
    void solicitarUrlAdjuntoComoMentorEsRechazado() {
        assertThatThrownBy(() -> service.solicitarUrl(new SolicitarUrlAdjuntoGuiaCommand(mentor, habitoId, "image/png")))
                .isInstanceOf(NotAuthorizedException.class);
        verify(almacenamientoPort, never()).firmarSubida(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void solicitarUrlAdjuntoComoAdminSuspendidoEsRechazado() {
        assertThatThrownBy(() -> service.solicitarUrl(
                new SolicitarUrlAdjuntoGuiaCommand(suspendedAdmin, habitoId, "image/png")))
                .isInstanceOf(NotAuthorizedException.class);
        verify(almacenamientoPort, never()).firmarSubida(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void confirmarAdjuntoArchivoCreaLaGuiaSiNoExisteYGuardaRutaCruda() {
        when(loadGuiaPort.porHabito(habitoId)).thenReturn(List.of());
        var command = new ConfirmarAdjuntoGuiaArchivoCommand(admin, habitoId, 3, SeccionGuia.QUE_HACER,
                TipoMedioGuia.IMAGEN, GuiaHabitoAdminService.BUCKET_ADJUNTOS_GUIA, "guias-habito/x/y", "image/png",
                12345, "foto.png", "Titulo");

        AdjuntoGuia adjunto = service.confirmar(command);

        assertThat(adjunto.tipoMedio()).isEqualTo(TipoMedioGuia.IMAGEN);
        assertThat(adjunto.rutaStorage()).isEqualTo("guias-habito/x/y");
        assertThat(adjunto.url()).isNull();
        assertThat(adjunto.orden()).isEqualTo(0);
        verify(saveGuiaPort).save(any());
        verify(almacenamientoPort, never()).firmarLectura(anyString(), any(Duration.class));
    }

    @Test
    void confirmarAdjuntoArchivoUsaLaGuiaExistenteYSiguienteOrden() {
        GuiaHabito existente = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), habitoId, 3, CLOCK.now());
        when(loadGuiaPort.porHabito(habitoId)).thenReturn(List.of(existente));
        when(loadAdjuntoPort.porGuias(List.of(existente.id()))).thenReturn(
                List.of(AdjuntoGuia.deEnlace(AdjuntoGuiaId.of(UUID.randomUUID()), existente.id(), SeccionGuia.QUE_HACER,
                        "https://a", null, 0, CLOCK.now())));
        var command = new ConfirmarAdjuntoGuiaArchivoCommand(admin, habitoId, 3, SeccionGuia.CIENCIA,
                TipoMedioGuia.AUDIO, GuiaHabitoAdminService.BUCKET_ADJUNTOS_GUIA, "guias-habito/x/z", "audio/mpeg",
                999, "audio.mp3", null);

        AdjuntoGuia adjunto = service.confirmar(command);

        assertThat(adjunto.orden()).isEqualTo(1);
        verify(saveGuiaPort, never()).save(any());
    }

    @Test
    void confirmarAdjuntoArchivoComoMentorEsRechazado() {
        var command = new ConfirmarAdjuntoGuiaArchivoCommand(mentor, habitoId, 3, SeccionGuia.QUE_HACER,
                TipoMedioGuia.IMAGEN, GuiaHabitoAdminService.BUCKET_ADJUNTOS_GUIA, "guias-habito/x/y", "image/png",
                123, "foto.png", null);

        assertThatThrownBy(() -> service.confirmar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveAdjuntoPort, never()).save(any());
    }

    @Test
    void confirmarAdjuntoArchivoComoAdminSuspendidoEsRechazado() {
        var command = new ConfirmarAdjuntoGuiaArchivoCommand(suspendedAdmin, habitoId, 3, SeccionGuia.QUE_HACER,
                TipoMedioGuia.IMAGEN, GuiaHabitoAdminService.BUCKET_ADJUNTOS_GUIA, "guias-habito/x/y", "image/png",
                123, "foto.png", null);

        assertThatThrownBy(() -> service.confirmar(command)).isInstanceOf(NotAuthorizedException.class);
        verify(saveAdjuntoPort, never()).save(any());
    }

    @Test
    void confirmarAdjuntoArchivoConTipoEnlaceEsRechazado() {
        when(loadGuiaPort.porHabito(habitoId)).thenReturn(List.of());
        var command = new ConfirmarAdjuntoGuiaArchivoCommand(admin, habitoId, 3, SeccionGuia.QUE_HACER,
                TipoMedioGuia.ENLACE, GuiaHabitoAdminService.BUCKET_ADJUNTOS_GUIA, "guias-habito/x/y", "image/png",
                123, "foto.png", null);

        assertThatThrownBy(() -> service.confirmar(command)).isInstanceOf(IllegalArgumentException.class);
        verify(saveAdjuntoPort, never()).save(any());
    }
}
