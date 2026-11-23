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
import com.renaser.os.points.api.RankingSemanalFinder;
import com.renaser.os.points.api.RankingSemanalFinder.PuestoDeLaSemana;
import com.renaser.os.chat.domain.model.ranking.TipoDeRankingSemanal;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
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
    private RankingSemanalFinder semanal;
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
        when(conversaciones.global(Formacion.ESCUELA)).thenReturn(Optional.of(Conversacion.crearGlobal(GLOBAL, AHORA)));
        when(mensajes.porId(any())).thenReturn(Optional.empty());
        when(almacenamiento.guardaObjetos()).thenReturn(true);
        when(dibujo.dibujar(any(), any())).thenReturn(PNG);
        when(delPrograma.enviarUnaVez(any())).thenReturn(2);
        when(ranking.alCorte(DOMINGO, Formacion.ESCUELA)).thenReturn(List.of(puesto("Liz Mendoza", "96.4"), puesto("Jorge Pérez", "92.1")));
    }

    @Test
    @DisplayName("publica la imagen sin aviso y el texto con aviso a todos, sin persona, con el ranking cerrado el domingo")
    void publica() {
        ResultadoDelPodio resultado = servicio(true).publicarLaSemanaCerrada();

        assertThat(resultado).isEqualTo(new ResultadoDelPodio(SEMANA.lunes(), DOMINGO, Estado.PUBLICADO, 2));
        verify(ranking).alCorte(DOMINGO, Formacion.ESCUELA);
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
    @DisplayName("D-367: salen cuatro mensajes seguidos (General, Coherencia, Kilómetros, Gimnasio), cada uno con su imagen, su texto y sus ids; solo el General avisa")
    void cuatroRankingsSeguidos() {
        conLosTresRankingsSemanales();

        ResultadoDelPodio resultado = servicio(true).publicarLaSemanaCerrada();

        assertThat(resultado.estado()).isEqualTo(Estado.PUBLICADO);
        assertThat(resultado.piezas()).isEqualTo(8);
        ArgumentCaptor<EntregaDelPrograma> entregas = ArgumentCaptor.forClass(EntregaDelPrograma.class);
        verify(delPrograma, org.mockito.Mockito.times(4)).enviarUnaVez(entregas.capture());
        List<EntregaDelPrograma> enOrden = entregas.getAllValues();
        TipoDeRankingSemanal[] orden = TipoDeRankingSemanal.values();
        for (int i = 0; i < 4; i++) {
            EntregaDelPrograma entrega = enOrden.get(i);
            assertThat(entrega.conversacionId()).isEqualTo(GLOBAL);
            assertThat(entrega.piezas().get(0).id()).isEqualTo(SEMANA.idDeLaImagen(Formacion.ESCUELA, orden[i]));
            assertThat(entrega.piezas().get(1).id()).isEqualTo(SEMANA.idDelTexto(Formacion.ESCUELA, orden[i]));
            assertThat(entrega.piezas().get(0).aviso()).isEqualTo(AvisoDeLaPieza.SIN_AVISO);
            assertThat(entrega.piezas().get(1).aviso()).isEqualTo(i == 0 ? AvisoDeLaPieza.A_TODOS : AvisoDeLaPieza.SIN_AVISO);
            verify(almacenamiento).subir(SEMANA.rutaDeLaImagen(Formacion.ESCUELA, orden[i]), PNG, "image/jpeg");
        }
        assertThat(enOrden.get(0).piezas().get(1).id()).as("el General conserva su id: no duplica la semana ya publicada").isEqualTo(SEMANA.idDelTexto());
        assertThat(enOrden.get(1).piezas().get(1).contenido().texto()).contains("Ana C.").containsIgnoringCase("coherencia");
        assertThat(enOrden.get(2).piezas().get(1).contenido().texto()).contains("Beto K.").containsIgnoringCase("kil");
        assertThat(enOrden.get(3).piezas().get(1).contenido().texto()).contains("Carla G.").containsIgnoringCase("entren");
        verify(semanal).coherencia(DOMINGO, Formacion.ESCUELA);
        verify(semanal).kilometros(DOMINGO, Formacion.ESCUELA);
        verify(semanal).gimnasio(DOMINGO, Formacion.ESCUELA);
    }

    @Test
    @DisplayName("D-367: un ranking sin nadie con valor no se publica y no frena a los otros")
    void rankingVacioNoFrena() {
        conLosTresRankingsSemanales();
        when(semanal.kilometros(DOMINGO, Formacion.ESCUELA)).thenReturn(List.of(valor("Beto Kilómetros", "0")));

        ResultadoDelPodio resultado = servicio(true).publicarLaSemanaCerrada();

        assertThat(resultado.estado()).isEqualTo(Estado.PUBLICADO);
        verify(delPrograma, org.mockito.Mockito.times(3)).enviarUnaVez(any());
        verify(almacenamiento, never()).subir(eq(SEMANA.rutaDeLaImagen(Formacion.ESCUELA, TipoDeRankingSemanal.KILOMETROS)), any(), anyString());
    }

    @Test
    @DisplayName("D-367: un ranking que falla no frena a los otros; el error sale al final y el reintento de la hora siguiente completa lo que falta")
    void unoFallaYElRestoSale() {
        conLosTresRankingsSemanales();
        doThrow(new IllegalStateException("S3 caído")).when(almacenamiento)
                .subir(eq(SEMANA.rutaDeLaImagen(Formacion.ESCUELA, TipoDeRankingSemanal.COHERENCIA)), any(), anyString());

        assertThatThrownBy(() -> servicio(true).publicarLaSemanaCerrada()).hasMessage("S3 caído");

        ArgumentCaptor<EntregaDelPrograma> entregas = ArgumentCaptor.forClass(EntregaDelPrograma.class);
        verify(delPrograma, org.mockito.Mockito.times(3)).enviarUnaVez(entregas.capture());
        assertThat(entregas.getAllValues()).extracting(e -> e.piezas().get(1).id()).containsExactly(
                SEMANA.idDelTexto(), SEMANA.idDelTexto(Formacion.ESCUELA, TipoDeRankingSemanal.KILOMETROS),
                SEMANA.idDelTexto(Formacion.ESCUELA, TipoDeRankingSemanal.GIMNASIO));

        // Reintento: ya están General, Kilómetros y Gimnasio; solo queda Coherencia.
        org.mockito.Mockito.reset(delPrograma, almacenamiento);
        when(almacenamiento.guardaObjetos()).thenReturn(true);
        when(delPrograma.enviarUnaVez(any())).thenReturn(2);
        for (TipoDeRankingSemanal tipo : List.of(TipoDeRankingSemanal.GENERAL, TipoDeRankingSemanal.KILOMETROS, TipoDeRankingSemanal.GIMNASIO)) {
            when(mensajes.porId(SEMANA.idDelTexto(Formacion.ESCUELA, tipo))).thenReturn(Optional.of(mock(Mensaje.class)));
        }

        ResultadoDelPodio reintento = servicio(true).publicarLaSemanaCerrada();

        assertThat(reintento.estado()).isEqualTo(Estado.PUBLICADO);
        verify(delPrograma, org.mockito.Mockito.times(1)).enviarUnaVez(entregas.capture());
        assertThat(entregas.getValue().piezas().get(1).id()).isEqualTo(SEMANA.idDelTexto(Formacion.ESCUELA, TipoDeRankingSemanal.COHERENCIA));
    }

    @Test
    @DisplayName("D-367: el General ya publicado (el de antes de este cambio) no se repite y los otros tres sí salen")
    void generalYaPublicado() {
        conLosTresRankingsSemanales();
        when(mensajes.porId(SEMANA.idDelTexto())).thenReturn(Optional.of(mock(Mensaje.class)));

        servicio(true).publicarLaSemanaCerrada();

        verify(delPrograma, org.mockito.Mockito.times(3)).enviarUnaVez(any());
        verify(ranking, never()).alCorte(any(), any());
    }

    @Test
    @DisplayName("D-367: la vista previa pedida por ranking y la de los cuatro, en orden, con su plantilla y su ranking")
    void vistasPreviasDeLosCuatro() {
        conLosTresRankingsSemanales();
        UserId admin = cuenta(UserRole.ADMIN, UserStatus.ACTIVE);

        List<VistaPreviaDelPodio> vistas = servicio(false).vistasPrevias(admin, Formacion.ESCUELA);

        assertThat(vistas).extracting(VistaPreviaDelPodio::ranking).containsExactly(TipoDeRankingSemanal.values());
        assertThat(vistas).allSatisfy(v -> assertThat(v.imagen()).isEqualTo(PNG));
        assertThat(vistas.get(2).texto()).contains("Beto K.");
        assertThat(vistas.get(2).plantilla()).isEqualTo(com.renaser.os.chat.domain.model.ranking.TextoDelPodio.plantillaDe(SEMANA, TipoDeRankingSemanal.KILOMETROS).name());
        assertThat(servicio(false).vistaPrevia(admin, Formacion.ESCUELA, TipoDeRankingSemanal.GIMNASIO).texto()).contains("Carla G.");
        assertThat(servicio(false).vistaPrevia(admin, Formacion.ESCUELA).ranking()).isEqualTo(TipoDeRankingSemanal.GENERAL);
        verifyNoInteractions(delPrograma);
    }

    private void conLosTresRankingsSemanales() {
        when(semanal.coherencia(DOMINGO, Formacion.ESCUELA)).thenReturn(List.of(valor("Ana Coherencia", "93.8")));
        when(semanal.kilometros(DOMINGO, Formacion.ESCUELA)).thenReturn(List.of(valor("Beto Kilómetros", "42.5")));
        when(semanal.gimnasio(DOMINGO, Formacion.ESCUELA)).thenReturn(List.of(valor("Carla Gimnasio", "5")));
    }

    private static PuestoDeLaSemana valor(String nombre, String valor) {
        return new PuestoDeLaSemana(UserId.of(UUID.randomUUID()), nombre, new BigDecimal(valor));
    }

    @Test
    @DisplayName("spec 024: cada formación publica SU podio en SU comunidad general, con SUS aprendices, y los ids de la Escuela no cambian")
    void unPodioPorFormacion() {
        ConversacionId deLaFormacion = ConversacionId.of(UUID.randomUUID());
        when(conversaciones.global(Formacion.DIPLOMADO)).thenReturn(Optional.of(Conversacion.crearGlobal(deLaFormacion, "Diplomado Renaser Global", AHORA, Formacion.DIPLOMADO)));
        when(ranking.alCorte(DOMINGO, Formacion.DIPLOMADO)).thenReturn(List.of(puesto("Rosa Quispe", "88.0")));

        ResultadoDelPodio resultado = servicio(true).publicarLaSemanaCerrada();

        assertThat(resultado.estado()).isEqualTo(Estado.PUBLICADO);
        ArgumentCaptor<EntregaDelPrograma> entregas = ArgumentCaptor.forClass(EntregaDelPrograma.class);
        verify(delPrograma, org.mockito.Mockito.times(2)).enviarUnaVez(entregas.capture());
        EntregaDelPrograma escuela = entregas.getAllValues().stream().filter(e -> e.conversacionId().equals(GLOBAL)).findFirst().orElseThrow();
        EntregaDelPrograma diplomado = entregas.getAllValues().stream().filter(e -> e.conversacionId().equals(deLaFormacion)).findFirst().orElseThrow();
        assertThat(escuela.piezas().get(1).id()).as("la semana ya publicada de la Escuela no se repite al desplegar").isEqualTo(SEMANA.idDelTexto());
        assertThat(escuela.piezas().get(1).contenido().texto()).contains("Liz M.").doesNotContain("Rosa");
        assertThat(diplomado.piezas().get(1).id()).isEqualTo(SEMANA.idDelTexto(Formacion.DIPLOMADO)).isNotEqualTo(SEMANA.idDelTexto());
        assertThat(diplomado.piezas().get(1).contenido().texto()).contains("Rosa Q.").doesNotContain("Liz");
        verify(almacenamiento).subir("ranking-semanal/2026-09-28-v1.jpg", PNG, "image/jpeg");
        verify(almacenamiento).subir(SEMANA.rutaDeLaImagen(Formacion.DIPLOMADO), PNG, "image/jpeg");
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
        when(ranking.alCorte(DOMINGO, Formacion.ESCUELA)).thenReturn(List.of(puesto("Liz Mendoza", "0"), puesto("Jorge Pérez", "0.0")));

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

        VistaPreviaDelPodio vista = servicio(false).vistaPrevia(admin, Formacion.ESCUELA);

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
            assertThatThrownBy(() -> servicio(true).vistaPrevia(actor, Formacion.ESCUELA)).isInstanceOf(NotAuthorizedException.class);
        }
        verifyNoInteractions(ranking, delPrograma);
    }

    private PodioDeLaSemanaService servicio(boolean activo) {
        return new PodioDeLaSemanaService(ranking, semanal, dibujo, almacenamiento, conversaciones, mensajes, delPrograma,
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
