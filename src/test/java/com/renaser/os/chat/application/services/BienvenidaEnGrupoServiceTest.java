package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort.Pendiente;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort.Pendientes;
import com.renaser.os.chat.application.ports.out.bienvenida.TextosDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La bienvenida en el chat del grupo estable (D-191), firmada por el programa, con el interruptor y
 * el corte de D-204. Sin
 * Spring: el {@code PlatformTransactionManager} sin stubbing hace que {@code TransactionTemplate}
 * ejecute el callback directo, como en {@code BienvenidaEnSoporteServiceTest}.
 */
@ExtendWith(MockitoExtension.class)
class BienvenidaEnGrupoServiceTest {

    /** 04:30 UTC: todavía es la víspera en Lima. La ventana es de horas, no de días locales. */
    private static final Instant AHORA = Instant.parse("2026-09-27T04:30:00Z");
    private static final Instant HACE_UNA_HORA = AHORA.minus(Duration.ofHours(1));
    private static final UUID GRUPO = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final ConversacionId CHAT = ConversacionId.of(UUID.fromString("33333333-3333-4333-8333-333333333333"));
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId LUIS = UserId.of(UUID.randomUUID());
    private static final UUID ASIG_ANA = UUID.randomUUID();
    private static final UUID ASIG_LUIS = UUID.randomUUID();

    @Mock
    private BienvenidaEnGrupoPort bienvenidaPort;
    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private EnviarMensajeDelProgramaUseCase delPrograma;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private PlatformTransactionManager transactionManager;

    /** Encendida (BIENVENIDA_ACTIVA=true). */
    private BienvenidaEnGrupoService servicio(String texto) {
        return servicio(texto, true);
    }

    private BienvenidaEnGrupoService servicio(String texto, boolean activa) {
        TextosDeBienvenidaPort textos = mock(TextosDeBienvenidaPort.class);
        if (activa) {
            when(textos.grupo()).thenReturn(texto);
        }
        return new BienvenidaEnGrupoService(bienvenidaPort, textos, loadConversacionPort, delPrograma,
                userSummaryFinder, transactionManager, FixedClock.at(AHORA), activa);
    }

    @Test
    @DisplayName("D-204: marca y después manda el texto del recurso en el chat del grupo, firmado por el PROGRAMA (a nombre de la aprendiz, no del mentor), con los dos primeros nombres")
    void mandaLaBienvenidaDelPrograma() {
        preparar(List.of(new Pendiente(ASIG_ANA, ANA, HACE_UNA_HORA)), UserStatus.ACTIVE);
        when(bienvenidaPort.marcarDada(ASIG_ANA)).thenReturn(true);

        int dadas = servicio("¡Hola, {nombre}! Te acompaña {mentor}.").darBienvenidas(GRUPO);

        assertThat(dadas).isEqualTo(1);
        InOrder orden = inOrder(bienvenidaPort, delPrograma);
        orden.verify(bienvenidaPort).marcarDada(ASIG_ANA);
        orden.verify(delPrograma).enviarDelPrograma(CHAT, ANA, ContenidoDelPrograma.texto("¡Hola, Ana! Te acompaña Carlos."));
        verify(delPrograma, never()).enviarDelPrograma(any(), eq(MENTOR), any());
    }

    @Test
    @DisplayName("D-204: el texto nuevo del recurso, dicho por el programa y sin género, con el nombre y el mentor puestos")
    void elTextoNuevoDelRecursoConLosReemplazos() {
        preparar(List.of(new Pendiente(ASIG_ANA, ANA, HACE_UNA_HORA)), UserStatus.ACTIVE);
        when(bienvenidaPort.marcarDada(ASIG_ANA)).thenReturn(true);

        servicio(textoDeGrupoDelRepo()).darBienvenidas(GRUPO);

        ArgumentCaptor<ContenidoDelPrograma> enviado = ArgumentCaptor.forClass(ContenidoDelPrograma.class);
        verify(delPrograma).enviarDelPrograma(eq(CHAT), eq(ANA), enviado.capture());
        assertThat(enviado.getValue().texto()).isEqualTo("¡Hola, Ana! 🌿 Qué alegría que te sumes a este grupo. "
                + "Aquí vas a compartir el camino con cada integrante y con Carlos, que te va a acompañar en estos "
                + "días. Este es tu espacio para contar tus avances, pedir apoyo y celebrar cada paso. "
                + "¡Te damos la bienvenida!");
    }

    @Test
    @DisplayName("D-204: apagada (el default) no consulta pendientes, no marca y no manda")
    void apagadaNoConsultaNiMarca() {
        assertThat(servicio("Hola {nombre}", false).darBienvenidas(GRUPO)).isZero();

        verifyNoInteractions(bienvenidaPort, loadConversacionPort, userSummaryFinder, delPrograma);
    }

    @Test
    @DisplayName("D-204: al prenderla no sale una bienvenida atrasada: quien entró hace 3 días no la recibe ni se marca; quien entró hace 1 h sí")
    void sinBienvenidasAtrasadas() {
        Instant haceTresDias = AHORA.minus(Duration.ofDays(3));
        preparar(List.of(new Pendiente(ASIG_ANA, ANA, HACE_UNA_HORA), new Pendiente(ASIG_LUIS, LUIS, haceTresDias)),
                UserStatus.ACTIVE);
        when(bienvenidaPort.marcarDada(ASIG_ANA)).thenReturn(true);

        assertThat(servicio("Hola {nombre}").darBienvenidas(GRUPO)).isEqualTo(1);

        verify(bienvenidaPort, never()).marcarDada(ASIG_LUIS);
        verify(delPrograma, times(1)).enviarDelPrograma(any(), any(), any());
        verify(delPrograma).enviarDelPrograma(CHAT, ANA, ContenidoDelPrograma.texto("Hola Ana"));
    }

    @Test
    @DisplayName("D-204: el borde de la ventana: 47 h todavía sí; 49 h ya no")
    void bordeDeLaVentana() {
        preparar(List.of(new Pendiente(ASIG_ANA, ANA, AHORA.minus(Duration.ofHours(47))),
                new Pendiente(ASIG_LUIS, LUIS, AHORA.minus(Duration.ofHours(49)))), UserStatus.ACTIVE);
        when(bienvenidaPort.marcarDada(ASIG_ANA)).thenReturn(true);

        assertThat(servicio("Hola {nombre}").darBienvenidas(GRUPO)).isEqualTo(1);
        verify(bienvenidaPort, never()).marcarDada(ASIG_LUIS);
    }

    @Test
    @DisplayName("D-204: si todos entraron antes de la ventana no busca el chat ni las cuentas")
    void todosFueraDeLaVentana() {
        when(bienvenidaPort.pendientes(GRUPO)).thenReturn(Optional.of(new Pendientes(MENTOR,
                List.of(new Pendiente(ASIG_ANA, ANA, AHORA.minus(Duration.ofDays(10)))))));

        assertThat(servicio("Hola {nombre}").darBienvenidas(GRUPO)).isZero();
        verifyNoInteractions(loadConversacionPort, userSummaryFinder, delPrograma);
        verify(bienvenidaPort, never()).marcarDada(any());
    }

    @Test
    @DisplayName("idempotente: si la marca ya estaba (otra entrega la dio), no manda nada")
    void reentregaNoDuplica() {
        preparar(List.of(new Pendiente(ASIG_ANA, ANA, HACE_UNA_HORA)), UserStatus.ACTIVE);
        when(bienvenidaPort.marcarDada(ASIG_ANA)).thenReturn(false);

        assertThat(servicio("Hola {nombre}").darBienvenidas(GRUPO)).isZero();
        verify(delPrograma, never()).enviarDelPrograma(any(), any(), any());
    }

    @Test
    @DisplayName("recepción, sin mentor o grupo no operativo (el puerto no devuelve pendientes): no manda nada")
    void sinPendientesNoManda() {
        when(bienvenidaPort.pendientes(GRUPO)).thenReturn(Optional.empty());

        assertThat(servicio("Hola {nombre}").darBienvenidas(GRUPO)).isZero();
        verifyNoInteractions(delPrograma, loadConversacionPort, userSummaryFinder);
    }

    @Test
    @DisplayName("sin texto de grupo en el recurso no consulta ni marca: las pertenencias quedan pendientes")
    void sinTextoNoMarca() {
        assertThat(servicio("").darBienvenidas(GRUPO)).isZero();
        verifyNoInteractions(bienvenidaPort, delPrograma);
    }

    @Test
    @DisplayName("mentor suspendido: no marca ni manda (queda pendiente)")
    void mentorSuspendidoNoManda() {
        preparar(List.of(new Pendiente(ASIG_ANA, ANA, HACE_UNA_HORA)), UserStatus.SUSPENDED);

        assertThat(servicio("Hola {nombre}").darBienvenidas(GRUPO)).isZero();
        verify(bienvenidaPort, never()).marcarDada(any());
        verify(delPrograma, never()).enviarDelPrograma(any(), any(), any());
    }

    @Test
    @DisplayName("un envío que falla no frena a los demás y se lanza al final para que el outbox reintente")
    void unFalloSeLanzaAlFinal() {
        preparar(List.of(new Pendiente(ASIG_ANA, ANA, HACE_UNA_HORA), new Pendiente(ASIG_LUIS, LUIS, HACE_UNA_HORA)),
                UserStatus.ACTIVE);
        when(bienvenidaPort.marcarDada(any())).thenReturn(true);
        when(delPrograma.enviarDelPrograma(any(), any(), any())).thenThrow(new IllegalStateException("base caída"))
                .thenReturn(null);

        assertThatThrownBy(() -> servicio("Hola {nombre}").darBienvenidas(GRUPO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("se dieron 1");
        verify(delPrograma, times(2)).enviarDelPrograma(any(), any(), any());
    }

    /** El texto {@code grupo} tal como está en el repo (el adaptador le quita el salto final). */
    private static String textoDeGrupoDelRepo() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("bienvenida/mensajes.yaml"));
        return yaml.getObject().getProperty("grupo").strip();
    }

    private void preparar(List<Pendiente> pendientes, UserStatus estadoMentor) {
        when(bienvenidaPort.pendientes(GRUPO)).thenReturn(Optional.of(new Pendientes(MENTOR, pendientes)));
        when(loadConversacionPort.porCelulaId(GRUPO)).thenReturn(Optional.of(
                Conversacion.crearCelula(CHAT, GRUPO, Instant.parse("2026-09-01T15:00:00Z"))));
        when(userSummaryFinder.findByIds(any())).thenReturn(Map.of(
                MENTOR, new UserSummary(MENTOR, "carlos ramírez", null, UserRole.MENTOR, estadoMentor),
                ANA, new UserSummary(ANA, "Ana Pérez", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                LUIS, new UserSummary(LUIS, "Luis Soto", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
    }
}
