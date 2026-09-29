package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase.FotoDelChat;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotosDelChatUseCase.TarjetasDelGrupo;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort.TarjetaConNombre;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.TipoConversacion;
import com.renaser.os.community.api.FotoPropiaDelGrupoFinder;
import com.renaser.os.community.api.FotoPropiaDelGrupoFinder.FotoPropia;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.FileSystemResource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Las tarjetas con nombre del chat: la del soporte (D-205) y la de cada integrante de un grupo o de un
 * soporte (D-206).
 */
@ExtendWith(MockitoExtension.class)
class FotosDelChatServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T15:00:00Z");
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId KELIN = UserId.of(UUID.randomUUID());
    private static final UserId RICARDO = UserId.of(UUID.randomUUID());
    private static final UUID GRUPO_FENIX = UUID.randomUUID();
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.randomUUID());
    private static final ConversacionId GLOBAL = ConversacionId.of(UUID.randomUUID());
    private static final ConversacionId GRUPO = ConversacionId.of(UUID.randomUUID());
    private static final ConversacionId DIRECTA = ConversacionId.of(UUID.randomUUID());
    private static final TarjetaConNombre TARJETA = new TarjetaConNombre(new byte[] {(byte) 0xFF, (byte) 0xD8}, "abc123");

    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private AutorizarAccesoAConversacionUseCase autorizarAcceso;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private TarjetaConNombrePort tarjetaPort;
    /** D-212: la foto propia de un grupo, que guarda community. */
    @Mock
    private FotoPropiaDelGrupoFinder fotosDeGrupos;

    private FotosDelChatService servicio;

    @BeforeEach
    void preparar() {
        servicio = conModo("TARJETA");
        conversacion(Conversacion.crearSoporte(SOPORTE, ANA, "Ana - Formación Renaser", AHORA));
        conversacion(Conversacion.crearGlobal(GLOBAL, AHORA));
        conversacion(Conversacion.crearCelula(GRUPO, GRUPO_FENIX, AHORA));
        conversacion(Conversacion.crearDirecta(DIRECTA, Conversacion.claveDirectaDe(ANA, RICARDO), AHORA));
        cuenta(ANA, "maría josé ñahui", UserRole.TRAINEE, UserStatus.ACTIVE);
        cuenta(KELIN, "Kelin Rojas", UserRole.ADMIN, UserStatus.ACTIVE);
        cuenta(RICARDO, "ricardo palomino", UserRole.MENTOR, UserStatus.ACTIVE);
        lenient().when(tarjetaPort.tarjetaDe(any())).thenReturn(TARJETA);
    }

    // ── La foto del soporte (D-205) ─────────────────────────────────────────

    @Test
    @DisplayName("soporte: la aprendiz ve SU tarjeta, con su primer nombre y la huella para el ETag")
    void laAprendizVeSuTarjeta() {
        when(autorizarAcceso.puedeVer(SOPORTE, ANA)).thenReturn(true);

        FotoDelChat foto = servicio.fotoDeLaConversacion(ANA, SOPORTE);

        verify(tarjetaPort).tarjetaDe("María");
        assertThat(foto.jpeg()).isSameAs(TARJETA.jpeg());
        assertThat(foto.huella()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("soporte: el staff ve la tarjeta de la aprendiz, no la suya")
    void elStaffVeLaTarjetaDeLaAprendiz() {
        when(autorizarAcceso.puedeVer(SOPORTE, KELIN)).thenReturn(true);

        servicio.fotoDeLaConversacion(KELIN, SOPORTE);

        verify(tarjetaPort).tarjetaDe("María");
    }

    @Test
    @DisplayName("soporte: quien no puede verlo recibe 403 y no se dibuja nada")
    void soporteSinAccesoEs403() {
        when(autorizarAcceso.puedeVer(SOPORTE, KELIN)).thenReturn(false);

        assertThatThrownBy(() -> servicio.fotoDeLaConversacion(KELIN, SOPORTE)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(tarjetaPort);
    }

    @Test
    @DisplayName("la comunidad y un 1 a 1 no tienen foto propia (404), aunque se pueda verlos")
    void laComunidadYUnUnoAUnoSon404() {
        when(autorizarAcceso.puedeVer(GLOBAL, ANA)).thenReturn(true);
        when(autorizarAcceso.puedeVer(DIRECTA, ANA)).thenReturn(true);

        assertThatThrownBy(() -> servicio.fotoDeLaConversacion(ANA, GLOBAL)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.fotoDeLaConversacion(ANA, DIRECTA)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(tarjetaPort, fotosDeGrupos);
    }

    // ── La foto propia de un grupo (D-212) ─────────────────────────────────

    @Test
    @DisplayName("grupo con foto propia: quien lo ve recibe esa foto, con la huella de su contenido para el ETag")
    void unGrupoConFotoPropiaLaSirve() {
        byte[] jpeg = {(byte) 0xFF, (byte) 0xD8, 7, 7, 7};
        when(autorizarAcceso.puedeVer(GRUPO, ANA)).thenReturn(true);
        when(fotosDeGrupos.fotoDe(GRUPO_FENIX)).thenReturn(Optional.of(new FotoPropia(jpeg, AHORA)));

        FotoDelChat foto = servicio.fotoDeLaConversacion(ANA, GRUPO);

        assertThat(foto.jpeg()).isSameAs(jpeg);
        assertThat(foto.huella()).hasSize(32).isNotEqualTo(TARJETA.huella());
        verifyNoInteractions(tarjetaPort);
    }

    @Test
    @DisplayName("grupo sin foto propia: 404 como antes, y la app muestra la tarjeta que trae")
    void unGrupoConLaFotoDeRenaserEs404() {
        when(autorizarAcceso.puedeVer(GRUPO, ANA)).thenReturn(true);
        when(fotosDeGrupos.fotoDe(GRUPO_FENIX)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.fotoDeLaConversacion(ANA, GRUPO)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("autorización negativa: quien no puede ver el grupo recibe 403 sin preguntarle a community")
    void laFotoDeUnGrupoAjenoEs403() {
        when(autorizarAcceso.puedeVer(GRUPO, KELIN)).thenReturn(false);

        assertThatThrownBy(() -> servicio.fotoDeLaConversacion(KELIN, GRUPO)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(fotosDeGrupos);
    }

    @Test
    @DisplayName("una conversación que no existe es 404, antes de preguntar el acceso")
    void laQueNoExisteEs404() {
        ConversacionId inexistente = ConversacionId.of(UUID.randomUUID());
        when(loadConversacionPort.porId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.fotoDeLaConversacion(ANA, inexistente)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.fotoDeIntegrante(ANA, inexistente, RICARDO))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(autorizarAcceso, tarjetaPort);
    }

    @Test
    @DisplayName("soporte: sin la cuenta de la aprendiz, la tarjeta va sin nombre")
    void sinLaCuentaDeLaAprendizVaSinNombre() {
        when(userSummaryFinder.findById(ANA)).thenReturn(Optional.empty());
        when(autorizarAcceso.puedeVer(SOPORTE, KELIN)).thenReturn(true);

        servicio.fotoDeLaConversacion(KELIN, SOPORTE);

        verify(tarjetaPort).tarjetaDe("");
    }

    // ── La foto de cada integrante (D-206) ──────────────────────────────────

    @Test
    @DisplayName("grupo: la aprendiz ve la tarjeta del MENTOR con el nombre del mentor")
    void enElGrupoSeVeLaTarjetaDelMentor() {
        when(autorizarAcceso.puedeVer(GRUPO, ANA)).thenReturn(true);
        when(autorizarAcceso.puedeVer(GRUPO, RICARDO)).thenReturn(true);

        FotoDelChat foto = servicio.fotoDeIntegrante(ANA, GRUPO, RICARDO);

        verify(tarjetaPort).tarjetaDe("Ricardo");
        assertThat(foto.huella()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("soporte: la aprendiz ve la tarjeta del staff que participa")
    void enElSoporteSeVeLaTarjetaDelStaff() {
        when(autorizarAcceso.puedeVer(SOPORTE, ANA)).thenReturn(true);
        when(autorizarAcceso.puedeVer(SOPORTE, KELIN)).thenReturn(true);

        servicio.fotoDeIntegrante(ANA, SOPORTE, KELIN);

        verify(tarjetaPort).tarjetaDe("Kelin");
    }

    @Test
    @DisplayName("autorización negativa: quien no puede ver el grupo recibe 403, antes de mirar al integrante")
    void integranteDeUnGrupoAjenoEs403() {
        when(autorizarAcceso.puedeVer(GRUPO, KELIN)).thenReturn(false);

        assertThatThrownBy(() -> servicio.fotoDeIntegrante(KELIN, GRUPO, RICARDO))
                .isInstanceOf(NotAuthorizedException.class);
        verify(autorizarAcceso, never()).puedeVer(GRUPO, RICARDO);
        verifyNoInteractions(tarjetaPort);
    }

    @Test
    @DisplayName("autorización negativa: una cuenta suspendida recibe 403")
    void suspendidaEs403() {
        cuenta(ANA, "Ana", UserRole.TRAINEE, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.fotoDeIntegrante(ANA, GRUPO, RICARDO))
                .isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.fotoDeLaConversacion(ANA, SOPORTE)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> servicio.fotoDeLaConversacion(ANA, GRUPO)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(tarjetaPort, fotosDeGrupos);
    }

    @Test
    @DisplayName("la comunidad y un 1 a 1 no tienen tarjetas de integrantes: 404 aunque se puedan ver")
    void comunidadYUnoAUnoSon404() {
        when(autorizarAcceso.puedeVer(GLOBAL, ANA)).thenReturn(true);
        when(autorizarAcceso.puedeVer(DIRECTA, ANA)).thenReturn(true);

        assertThatThrownBy(() -> servicio.fotoDeIntegrante(ANA, GLOBAL, RICARDO))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.fotoDeIntegrante(ANA, DIRECTA, RICARDO))
                .isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(tarjetaPort);
    }

    @Test
    @DisplayName("alguien que no es integrante del grupo: 404, sin dibujar su tarjeta")
    void quienNoEsIntegranteEs404() {
        when(autorizarAcceso.puedeVer(GRUPO, ANA)).thenReturn(true);
        when(autorizarAcceso.puedeVer(GRUPO, KELIN)).thenReturn(false);

        assertThatThrownBy(() -> servicio.fotoDeIntegrante(ANA, GRUPO, KELIN)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(tarjetaPort);
    }

    // ── A quiénes se les manda la tarjeta: el modo del servidor (D-206) ──────

    @Test
    @DisplayName("modo TARJETA (el default): todos llevan su tarjeta, aunque hayan subido foto, y no se consulta a nadie")
    void conTarjetaLaLlevanTodos() {
        grupoConChat();

        TarjetasDelGrupo tarjetas = servicio.tarjetasDelGrupo(GRUPO_FENIX, List.of(RICARDO, ANA)).orElseThrow();

        assertThat(tarjetas.chat()).as("se sirven en el chat del grupo").isEqualTo(GRUPO);
        assertThat(tarjetas.conTarjeta()).containsExactly(RICARDO, ANA);
        verify(userSummaryFinder, never()).findByIds(any());
    }

    @Test
    @DisplayName("modo FOTO_SUBIDA: la tarjeta solo para quien no subió foto; quien la subió se muestra con ella")
    void conFotoSubidaLaLlevanLosQueNoSubieron() {
        grupoConChat();
        UserId sinCuenta = UserId.of(UUID.randomUUID());
        when(userSummaryFinder.findByIds(List.of(RICARDO, ANA, KELIN, sinCuenta))).thenReturn(Map.of(
                RICARDO, new UserSummary(RICARDO, "Ricardo Palomino", "https://fotos/ricardo.jpg", UserRole.MENTOR, UserStatus.ACTIVE),
                ANA, new UserSummary(ANA, "Ana", null, UserRole.TRAINEE, UserStatus.ACTIVE),
                KELIN, new UserSummary(KELIN, "Kelin", "  ", UserRole.ADMIN, UserStatus.ACTIVE)));

        TarjetasDelGrupo tarjetas = conModo("FOTO_SUBIDA")
                .tarjetasDelGrupo(GRUPO_FENIX, List.of(RICARDO, ANA, KELIN, sinCuenta)).orElseThrow();

        assertThat(tarjetas.conTarjeta()).as("Ricardo subió foto; una URL en blanco no es una foto")
                .containsExactly(ANA, KELIN, sinCuenta);
    }

    @Test
    @DisplayName("un modo mal escrito no tumba el arranque: queda TARJETA; mayúsculas y espacios no importan")
    void unModoMalEscritoQuedaEnTarjeta() {
        grupoConChat();

        assertThat(conModo("tarjetas").tarjetasDelGrupo(GRUPO_FENIX, List.of(RICARDO)).orElseThrow().conTarjeta())
                .containsExactly(RICARDO);
        assertThat(conModo("").tarjetasDelGrupo(GRUPO_FENIX, List.of(RICARDO)).orElseThrow().conTarjeta())
                .containsExactly(RICARDO);
        verify(userSummaryFinder, never()).findByIds(any());
        when(userSummaryFinder.findByIds(List.of(RICARDO))).thenReturn(Map.of(RICARDO,
                new UserSummary(RICARDO, "Ricardo", "https://fotos/ricardo.jpg", UserRole.MENTOR, UserStatus.ACTIVE)));
        assertThat(conModo(" foto_subida ").tarjetasDelGrupo(GRUPO_FENIX, List.of(RICARDO)).orElseThrow().conTarjeta())
                .isEmpty();
    }

    @Test
    @DisplayName("conTarjetaEn: solo un grupo o un soporte tienen tarjetas; la comunidad y el 1 a 1 no, y sin nadie no consulta")
    void tarjetasPorTipoDeConversacion() {
        var integrantes = List.of(ANA, KELIN);

        assertThat(servicio.conTarjetaEn(TipoConversacion.CELULA, integrantes)).containsExactlyInAnyOrder(ANA, KELIN);
        assertThat(servicio.conTarjetaEn(TipoConversacion.SOPORTE, integrantes)).containsExactlyInAnyOrder(ANA, KELIN);
        assertThat(servicio.conTarjetaEn(TipoConversacion.GLOBAL, integrantes)).isEmpty();
        assertThat(servicio.conTarjetaEn(TipoConversacion.DIRECTA, integrantes)).isEmpty();
        assertThat(servicio.conTarjetaEn(TipoConversacion.CELULA, List.of())).isEmpty();
    }

    @Test
    @DisplayName("un grupo sin chat no tiene dónde servir las tarjetas: vacío")
    void unGrupoSinChat() {
        UUID sinChat = UUID.randomUUID();
        when(loadConversacionPort.porCelulaId(sinChat)).thenReturn(Optional.empty());

        assertThat(servicio.tarjetasDelGrupo(sinChat, List.of(ANA))).isEmpty();
    }

    @Test
    @DisplayName("application.yaml trae el modo con TARJETA por defecto, la decisión del dueño")
    void elDefaultEsLaTarjeta() {
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        // Del disco y no del classpath: en las pruebas src/test/resources/application.yaml la tapa (E-318).
        yaml.setResources(new FileSystemResource("src/main/resources/application.yaml"));

        assertThat(yaml.getObject().getProperty("renaser.chat.foto-de-integrantes"))
                .isEqualTo("${CHAT_FOTO_DE_INTEGRANTES:TARJETA}");
    }

    private FotosDelChatService conModo(String modo) {
        return new FotosDelChatService(loadConversacionPort, autorizarAcceso, userSummaryFinder, tarjetaPort,
                fotosDeGrupos, modo);
    }

    private void grupoConChat() {
        when(loadConversacionPort.porCelulaId(GRUPO_FENIX)).thenReturn(Optional.of(
                Conversacion.crearCelula(GRUPO, GRUPO_FENIX, AHORA)));
    }

    private void conversacion(Conversacion conversacion) {
        lenient().when(loadConversacionPort.porId(conversacion.id())).thenReturn(Optional.of(conversacion));
    }

    private void cuenta(UserId id, String nombre, UserRole rol, UserStatus estado) {
        lenient().when(userSummaryFinder.findById(id)).thenReturn(Optional.of(new UserSummary(id, nombre, null, rol, estado)));
    }
}
