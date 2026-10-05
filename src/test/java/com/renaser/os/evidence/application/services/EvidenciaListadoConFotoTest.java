package com.renaser.os.evidence.application.services;

import com.renaser.os.evidence.api.DestinoEvidencia;
import com.renaser.os.evidence.api.EstadoValidacion;
import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaAdminUseCase.ListarEvidenciaAdminComando;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.EvidenciaListada;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.ListarEvidenciaComando;
import com.renaser.os.evidence.application.ports.in.evidencia.ListarEvidenciaUseCase.PaginaEvidencias;
import com.renaser.os.evidence.application.ports.out.evidencia.LoadEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.evidencia.SaveEvidenciaPort;
import com.renaser.os.evidence.application.ports.out.ia.ValidacionIAPort;
import com.renaser.os.evidence.domain.model.evidencia.Evidencia;
import com.renaser.os.evidence.domain.model.evidencia.EvidenciaId;
import com.renaser.os.points.api.AjustarPuntosPort;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
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

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * D-252 (2026-10-05): el listado de la app ({@code GET /api/v1/evidence}) trae la foto firmada de cada
 * evidencia, para que Yo muestre la foto real y no un ícono de cámara.
 *
 * <p>Lo que importa, además de que la URL llegue: que se firme DESPUÉS de autorizar (quien no puede ver
 * la evidencia no recibe ninguna llave, ni siquiera se calcula), que se firme solo lo que es una imagen
 * y solo la página que se devuelve, y con la misma validez que el chat.
 */
@ExtendWith(MockitoExtension.class)
class EvidenciaListadoConFotoTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-05T10:00:00Z"));
    private static final Duration VALIDEZ_DEL_CHAT = Duration.ofMinutes(15);

    @Mock
    private LoadEvidenciaPort loadEvidenciaPort;
    @Mock
    private SaveEvidenciaPort saveEvidenciaPort;
    @Mock
    private ValidacionIAPort validacionIAPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private ParticipacionProgramaFinder participacionFinder;
    @Mock
    private AjustarPuntosPort ajustarPuntosPort;
    @Mock
    private AlmacenamientoPort almacenamientoPort;
    @Mock
    private IdGenerator idGenerator;

    private EvidenciaService service;

    @BeforeEach
    void setUp() {
        service = new EvidenciaService(loadEvidenciaPort, saveEvidenciaPort, validacionIAPort, userSummaryFinder,
                participacionFinder, ajustarPuntosPort, almacenamientoPort, CLOCK, idGenerator);
        lenient().when(almacenamientoPort.firmarLectura(anyString(), any()))
                .thenAnswer(inv -> URI.create("https://s3.test/" + inv.getArgument(0) + "?firma=x"));
    }

    @Test
    @DisplayName("el dueño recibe la URL firmada de cada foto (y de la captura); la de texto, video y audio viaja null")
    void elDuenoRecibeLaFotoFirmadaDeCadaImagen() {
        UserId dueno = activo(UserRole.TRAINEE);
        Evidencia foto = conArchivo(dueno, TipoEvidencia.FOTO, "evidencia-habitos/a/foto");
        Evidencia captura = conArchivo(dueno, TipoEvidencia.CAPTURA, "evidencia-habitos/a/captura");
        Evidencia video = conArchivo(dueno, TipoEvidencia.VIDEO, "evidencia-habitos/a/video");
        Evidencia audio = conArchivo(dueno, TipoEvidencia.AUDIO, "evidencia-habitos/a/audio");
        Evidencia texto = deTexto(dueno);
        when(loadEvidenciaPort.buscar(any(), any(), eq(20))).thenReturn(List.of(foto, captura, video, audio, texto));

        PaginaEvidencias pagina = service.listar(new ListarEvidenciaComando(dueno, null, null, null, null, null, null));

        assertThat(pagina.evidencias()).extracting(EvidenciaListada::fotoUrl).containsExactly(
                "https://s3.test/evidencia-habitos/a/foto?firma=x",
                "https://s3.test/evidencia-habitos/a/captura?firma=x", null, null, null);
        verify(almacenamientoPort).firmarLectura("evidencia-habitos/a/foto", VALIDEZ_DEL_CHAT);
        verify(almacenamientoPort).firmarLectura("evidencia-habitos/a/captura", VALIDEZ_DEL_CHAT);
        verify(almacenamientoPort, never()).firmarLectura(eq("evidencia-habitos/a/video"), any());
        verify(almacenamientoPort, never()).firmarLectura(eq("evidencia-habitos/a/audio"), any());
    }

    @Test
    @DisplayName("un aprendiz que pide la evidencia de otro: 403 y no se firma nada")
    void pedirLaAjenaNoFirmaNada() {
        UserId aprendiz = activo(UserRole.TRAINEE);
        UserId otro = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> service.listar(new ListarEvidenciaComando(aprendiz, otro, null, null, null, null,
                null))).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(loadEvidenciaPort, almacenamientoPort);
    }

    @Test
    @DisplayName("cuenta suspendida: 403 y no se firma nada, aunque tenga fotos propias")
    void suspendidoNoRecibeLlaves() {
        UserId suspendido = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findById(suspendido)).thenReturn(Optional.of(
                new UserSummary(suspendido, "Fixture", null, UserRole.TRAINEE, UserStatus.SUSPENDED)));

        assertThatThrownBy(() -> service.listar(new ListarEvidenciaComando(suspendido, null, null, null, null, null,
                null))).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(almacenamientoPort);
    }

    @Test
    @DisplayName("el mentor asignado ve la foto de su aprendiz (ya podía abrirla por /url); el no asignado, 403 sin firma")
    void elMentorAsignadoSiElOtroNo() {
        UserId mentor = activo(UserRole.MENTOR);
        UserId otroMentor = activo(UserRole.MENTOR);
        UserId aprendiz = UserId.of(UUID.randomUUID());
        when(participacionFinder.deParticipante(aprendiz)).thenReturn(Optional.of(participacionDe(aprendiz, mentor)));
        when(loadEvidenciaPort.buscar(any(), any(), eq(20)))
                .thenReturn(List.of(conArchivo(aprendiz, TipoEvidencia.FOTO, "evidencia-habitos/b/foto")));

        PaginaEvidencias delAsignado = service.listar(new ListarEvidenciaComando(mentor, aprendiz, null, null, null,
                null, null));

        assertThat(delAsignado.evidencias().getFirst().fotoUrl()).isEqualTo("https://s3.test/evidencia-habitos/b/foto?firma=x");
        assertThatThrownBy(() -> service.listar(new ListarEvidenciaComando(otroMentor, aprendiz, null, null, null,
                null, null))).isInstanceOf(NotAuthorizedException.class);
        verify(almacenamientoPort, times(1)).firmarLectura(anyString(), any());
    }

    @Test
    @DisplayName("se firma solo la página que se devuelve: con 21 fotos, 20 firmas")
    void firmaSoloLaPagina() {
        UserId dueno = activo(UserRole.TRAINEE);
        List<Evidencia> veintiuna = new ArrayList<>();
        for (int i = 0; i < 21; i++) {
            veintiuna.add(conArchivo(dueno, TipoEvidencia.FOTO, "evidencia-habitos/c/" + i));
        }
        when(loadEvidenciaPort.buscar(any(), any(), eq(20))).thenReturn(veintiuna);

        PaginaEvidencias pagina = service.listar(new ListarEvidenciaComando(dueno, null, null, null, null, null, null));

        assertThat(pagina.evidencias()).hasSize(20).allSatisfy(fila -> assertThat(fila.fotoUrl()).isNotNull());
        verify(almacenamientoPort, times(20)).firmarLectura(anyString(), eq(VALIDEZ_DEL_CHAT));
        verify(almacenamientoPort, never()).firmarLectura(eq("evidencia-habitos/c/20"), any());
    }

    @Test
    @DisplayName("el listado del panel admin no firma: no hay pantalla que muestre esa foto")
    void elListadoDelPanelNoFirma() {
        UserId admin = activo(UserRole.ADMIN);
        when(loadEvidenciaPort.buscar(any(), any(), eq(20)))
                .thenReturn(List.of(conArchivo(UserId.of(UUID.randomUUID()), TipoEvidencia.FOTO, "evidencia-habitos/d/foto")));

        PaginaEvidencias pagina = service.listar(new ListarEvidenciaAdminComando(admin, null,
                EstadoValidacion.VALIDA, null, null, null, null));

        assertThat(pagina.evidencias()).singleElement().satisfies(fila -> assertThat(fila.fotoUrl()).isNull());
        verifyNoInteractions(almacenamientoPort);
    }

    // ── Semilla ─────────────────────────────────────────────────────────────

    private UserId activo(UserRole rol) {
        UserId id = UserId.of(UUID.randomUUID());
        lenient().when(userSummaryFinder.findById(id))
                .thenReturn(Optional.of(new UserSummary(id, "Fixture", null, rol, UserStatus.ACTIVE)));
        return id;
    }

    private static Evidencia conArchivo(UserId participante, TipoEvidencia tipo, String ruta) {
        return Evidencia.registrar(EvidenciaId.of(UUID.randomUUID()), participante,
                new DestinoEvidencia.RegistroHabito(UUID.randomUUID()), tipo, "s3-renaser90dias", ruta, null, null,
                null, null, false, CLOCK.now(), CLOCK);
    }

    private static Evidencia deTexto(UserId participante) {
        return Evidencia.registrar(EvidenciaId.of(UUID.randomUUID()), participante,
                new DestinoEvidencia.RegistroHabito(UUID.randomUUID()), TipoEvidencia.TEXTO, null, null, "hecho",
                null, null, null, false, CLOCK.now(), CLOCK);
    }

    private static ParticipacionPrograma participacionDe(UserId participanteId, UserId mentorId) {
        return new ParticipacionPrograma(participanteId, true, 20, LocalDate.of(2026, 1, 1),
                ZoneId.of("America/Lima"), FasePrograma.initial(), null, mentorId, UserRole.TRAINEE, false, true);
    }
}
