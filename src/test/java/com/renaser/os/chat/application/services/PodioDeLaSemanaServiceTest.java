package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.AvisoDeLaPieza;
import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase.EntregaDelPrograma;
import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase.Estado;
import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase.ResultadoDelPodio;
import com.renaser.os.chat.application.ports.in.ranking.VerPodioDeLaSemanaUseCase.VistaPreviaDelPodio;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.mensaje.LoadMensajePort;
import com.renaser.os.chat.application.ports.out.ranking.DibujarPodioPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.ranking.SemanaDelRanking;
import com.renaser.os.points.api.RankingGeneralFinder;
import com.renaser.os.points.api.RankingGeneralFinder.PuestoEnElRankingGeneral;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El podio semanal (D-262) sin Spring. Reloj a las 03:00 UTC del martes 6 de octubre = 22:00 del lunes 5 en Lima:
 * la semana cerrada es la del 28 de septiembre al 4 de octubre (con la fecha del servidor, el martes 6, sería la
 * misma, así que el caso de la madrugada que cambia de semana va en {@code SemanaDelRankingTest}).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PodioDeLaSemanaServiceTest {

    private static final Instant AHORA = Instant.parse("2026-10-06T03:00:00Z");
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 4);
    private static final SemanaDelRanking SEMANA = new SemanaDelRanking(LocalDate.of(2026, 9, 28));
    private static final ConversacionId GLOBAL = ConversacionId.of(UUID.randomUUID());
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Mock
    private RankingGeneralFinder ranking;
    @Mock
    private DibujarPodioPort dibujo;
    @Mock
    private AlmacenamientoPort almacenamiento;
    @Mock
    private LoadConversacionPort conversaciones;
    @Mock
    private LoadMensajePort mensajes;
    @Mock
    private EnviarMensajeDelProgramaUseCase delPrograma;
    @Mock
    private UserSummaryFinder usuarios;

    @BeforeEach
    void grupoGeneral() {
        when(conversaciones.global()).thenReturn(Optional.of(Conversacion.crearGlobal(GLOBAL, AHORA)));
        when(mensajes.porId(any())).thenReturn(Optional.empty());
        when(almacenamiento.guardaObjetos()).thenReturn(true);
        when(dibujo.dibujar(any(), any())).thenReturn(PNG);
        when(delPrograma.enviarUnaVez(any())).thenReturn(2);
        when(ranking.alCorte(DOMINGO)).thenReturn(List.of(puesto("Liz Mendoza", "96.4"), puesto("Jorge Pérez", "92.1")));
    }

    @Test
    @DisplayName("publica la imagen sin aviso y el texto con aviso a todos, sin persona, con el ranking cerrado el domingo")
    void publica() {
        ResultadoDelPodio resultado = servicio(true).publicarLaSemanaCerrada();

        assertThat(resultado).isEqualTo(new ResultadoDelPodio(SEMANA.lunes(), DOMINGO, Estado.PUBLICADO, 2));
        verify(ranking).alCorte(DOMINGO);
        verify(almacenamiento).subir("ranking-semanal/2026-09-28-v1.jpg", PNG, "image/jpeg");
        EntregaDelPrograma entrega = entregada();
        assertThat(entrega.conversacionId()).isEqualTo(GLOBAL);
        assertThat(entrega.sobreQuien()).as("sin persona: no cae con la cuenta de nadie").isNull();
        assertThat(entrega.piezas()).hasSize(2);
        assertThat(entrega.piezas().get(0).id()).isEqualTo(SEMANA.idDeLaImagen());
        assertThat(entrega.piezas().get(0).aviso()).isEqualTo(AvisoDeLaPieza.SIN_AVISO);
        assertThat(entrega.piezas().get(0).contenido().mediaRuta()).isEqualTo("ranking-semanal/2026-09-28-v1.jpg");
        assertThat(entrega.piezas().get(1).id()).isEqualTo(SEMANA.idDelTexto());
        assertThat(entrega.piezas().get(1).aviso()).isEqualTo(AvisoDeLaPieza.A_TODOS);
        assertThat(entrega.piezas().get(1).contenido().texto()).contains("Liz M.").contains("Jorge P.");
    }

    @Test
    @DisplayName("apagado no lee el ranking ni publica")
    void apagado() {
        ResultadoDelPodio resultado = servicio(false).publicarLaSemanaCerrada();

        assertThat(resultado.estado()).isEqualTo(Estado.APAGADO);
        verifyNoInteractions(ranking, dibujo, delPrograma);
    }

    @Test
    @DisplayName("si el texto de la semana ya está, no mira nada más")
    void yaEstaba() {
        when(mensajes.porId(SEMANA.idDelTexto())).thenReturn(Optional.of(mock(Mensaje.class)));

        assertThat(servicio(true).publicarLaSemanaCerrada().estado()).isEqualTo(Estado.YA_ESTABA);
        verifyNoInteractions(ranking, dibujo, delPrograma);
        verify(almacenamiento, never()).subir(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("si nadie tiene puntaje, no se publica")
    void sinPuntajes() {
        when(ranking.alCorte(DOMINGO)).thenReturn(List.of(puesto("Liz Mendoza", "0"), puesto("Jorge Pérez", "0.0")));

        assertThat(servicio(true).publicarLaSemanaCerrada().estado()).isEqualTo(Estado.SIN_PUNTAJES);
        verifyNoInteractions(dibujo, delPrograma);
    }

    @Test
    @DisplayName("sin almacenamiento de verdad sale solo el texto")
    void sinAlmacenamiento() {
        when(almacenamiento.guardaObjetos()).thenReturn(false);

        servicio(true).publicarLaSemanaCerrada();

        assertThat(entregada().piezas()).singleElement().satisfies(p -> assertThat(p.id()).isEqualTo(SEMANA.idDelTexto()));
        verifyNoInteractions(dibujo);
    }

    @Test
    @DisplayName("si S3 falla no sale nada: la imagen es el podio y la próxima hora lo reintenta")
    void s3Falla() {
        doThrow(new IllegalStateException("S3 caído")).when(almacenamiento).subir(anyString(), any(), anyString());

        assertThatThrownBy(() -> servicio(true).publicarLaSemanaCerrada()).hasMessage("S3 caído");
        verifyNoInteractions(delPrograma);
    }

    @Test
    @DisplayName("publicar ahora: Administración lo publica aunque el interruptor esté apagado")
    void publicarAhora() {
        UserId admin = cuenta(UserRole.ALCHEMIST, UserStatus.ACTIVE);

        assertThat(servicio(false).publicarAhora(admin).estado()).isEqualTo(Estado.PUBLICADO);
    }

    @Test
    @DisplayName("vista previa: imagen y texto de la semana, sin publicar nada")
    void vistaPrevia() {
        UserId admin = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);

        VistaPreviaDelPodio vista = servicio(false).vistaPrevia(admin);

        assertThat(vista.lunes()).isEqualTo(SEMANA.lunes());
        assertThat(vista.puestos()).hasSize(2);
        assertThat(vista.texto()).contains("Liz M.");
        assertThat(vista.imagen()).isEqualTo(PNG);
        assertThat(vista.yaPublicado()).isFalse();
        verifyNoInteractions(delPrograma);
        verify(almacenamiento, never()).subir(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("autorización negativa: MENTOR, TRAINEE y un ADMIN suspendido reciben 403 y no se publica nada")
    void soloAdministracion() {
        for (UserId actor : List.of(cuenta(UserRole.MENTOR, UserStatus.ACTIVE), cuenta(UserRole.TRAINEE, UserStatus.ACTIVE),
                cuenta(UserRole.ADMIN, UserStatus.SUSPENDED))) {
            assertThatThrownBy(() -> servicio(true).publicarAhora(actor)).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> servicio(true).vistaPrevia(actor)).isInstanceOf(NotAuthorizedException.class);
        }
        verifyNoInteractions(ranking, delPrograma);
    }

    private PodioDeLaSemanaService servicio(boolean activo) {
        return new PodioDeLaSemanaService(ranking, dibujo, almacenamiento, conversaciones, mensajes, delPrograma,
                usuarios, FixedClock.at(AHORA), activo);
    }

    private EntregaDelPrograma entregada() {
        ArgumentCaptor<EntregaDelPrograma> entrega = ArgumentCaptor.forClass(EntregaDelPrograma.class);
        verify(delPrograma).enviarUnaVez(entrega.capture());
        return entrega.getValue();
    }

    private UserId cuenta(UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        when(usuarios.findById(eq(id))).thenReturn(Optional.of(new UserSummary(id, "Kelin Rojas", null, rol, estado)));
        return id;
    }

    private static PuestoEnElRankingGeneral puesto(String nombre, String puntaje) {
        return new PuestoEnElRankingGeneral(UserId.of(UUID.randomUUID()), nombre, new BigDecimal(puntaje));
    }
}
