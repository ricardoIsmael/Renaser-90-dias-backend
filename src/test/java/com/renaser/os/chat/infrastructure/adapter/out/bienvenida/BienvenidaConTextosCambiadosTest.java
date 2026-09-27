package com.renaser.os.chat.infrastructure.adapter.out.bienvenida;

import com.renaser.os.chat.application.ports.in.mensaje.EnviarMensajeDelProgramaUseCase;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort.Pendiente;
import com.renaser.os.chat.application.ports.out.bienvenida.BienvenidaEnGrupoPort.Pendientes;
import com.renaser.os.chat.application.ports.out.bienvenida.CambiosDeBienvenidaEnMemoria;
import com.renaser.os.chat.application.ports.out.bienvenida.DibujarBienvenidaPort;
import com.renaser.os.chat.application.ports.out.bienvenida.MarcaDeBienvenidaPort;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.services.BienvenidaEnGrupoService;
import com.renaser.os.chat.application.services.BienvenidaEnSoporteService;
import com.renaser.os.chat.domain.model.bienvenida.CambioDeBienvenida;
import com.renaser.os.chat.domain.model.bienvenida.PiezaDeBienvenida;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.mensaje.ContenidoDelPrograma;
import com.renaser.os.chat.domain.model.mensaje.Mensaje;
import com.renaser.os.chat.domain.model.mensaje.MensajeId;
import com.renaser.os.shared.application.ports.out.AlmacenamientoPort;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Lo que Administración guarda desde la app es lo que sale en la PRÓXIMA bienvenida, y al volver al
 * original sale otra vez el del repo (D-210). Los servicios que mandan la bienvenida, de verdad; los
 * textos, los vigentes de verdad (bitácora en memoria + {@code mensajes.yaml} del repo); el resto, dobles.
 */
class BienvenidaConTextosCambiadosTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T04:30:00Z");
    private static final UserId KELIN = UserId.of(UUID.fromString("22222222-2222-4222-8222-222222222222"));
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.fromString("55555555-5555-4555-8555-555555555555"));

    private final CambiosDeBienvenidaEnMemoria cambios = new CambiosDeBienvenidaEnMemoria();
    private final TextosDeBienvenidaYamlAdapter delRepo = new TextosDeBienvenidaYamlAdapter();
    private final TextosDeBienvenidaVigentes textos = new TextosDeBienvenidaVigentes(delRepo, cambios);
    private final EnviarMensajeDelProgramaUseCase delPrograma = mock(EnviarMensajeDelProgramaUseCase.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);

    @Test
    @DisplayName("soporte: el formal guardado sale en la próxima bienvenida; al volver al original, sale el del repo")
    void enElSoporte() {
        when(delPrograma.enviarDelPrograma(any(), any(), any())).thenAnswer(inv -> Mensaje.delPrograma(
                MensajeId.of(UUID.randomUUID()), inv.getArgument(0), inv.getArgument(1), inv.getArgument(2), AHORA));
        BienvenidaEnSoporteService soporte = servicioDelSoporte();

        assertThat(formalQueRecibe(soporte, "Ana Pérez")).isEqualTo(delRepo.soporteFormal().replace("{nombre}", "Ana"));

        cambios.registrar(CambioDeBienvenida.texto(PiezaDeBienvenida.SOPORTE_FORMAL,
                "Hola, {nombre}. Te damos la bienvenida oficial a la Formación Renaser.", KELIN, AHORA));
        assertThat(formalQueRecibe(soporte, "Luis Soto"))
                .isEqualTo("Hola, Luis. Te damos la bienvenida oficial a la Formación Renaser.");

        cambios.registrar(CambioDeBienvenida.volverAlOriginal(PiezaDeBienvenida.SOPORTE_FORMAL, KELIN, AHORA));
        assertThat(formalQueRecibe(soporte, "Eva Díaz")).isEqualTo(delRepo.soporteFormal().replace("{nombre}", "Eva"));
    }

    @Test
    @DisplayName("grupo: el texto guardado sale en la próxima bienvenida del grupo, con el nombre y el mentor puestos")
    void enElGrupo() {
        UUID grupo = UUID.randomUUID();
        ConversacionId chat = ConversacionId.of(UUID.randomUUID());
        UserId mentor = UserId.of(UUID.randomUUID());
        UserId ana = UserId.of(UUID.randomUUID());
        UUID asignacion = UUID.randomUUID();
        BienvenidaEnGrupoPort pendientes = mock(BienvenidaEnGrupoPort.class);
        LoadConversacionPort conversaciones = mock(LoadConversacionPort.class);
        when(pendientes.pendientes(grupo)).thenReturn(Optional.of(new Pendientes(mentor,
                List.of(new Pendiente(asignacion, ana, AHORA.minusSeconds(60))))));
        when(pendientes.marcarDada(asignacion)).thenReturn(true);
        when(conversaciones.porCelulaId(grupo)).thenReturn(Optional.of(Conversacion.crearCelula(chat, grupo, AHORA)));
        when(usuarios.findByIds(any())).thenReturn(Map.of(
                mentor, new UserSummary(mentor, "carlos ramírez", null, UserRole.MENTOR, UserStatus.ACTIVE),
                ana, new UserSummary(ana, "Ana Pérez", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        cambios.registrar(CambioDeBienvenida.texto(PiezaDeBienvenida.GRUPO,
                "¡{nombre}, qué bueno tenerte! {mentor} te acompaña.", KELIN, AHORA));

        new BienvenidaEnGrupoService(pendientes, textos, conversaciones, delPrograma, usuarios,
                mock(PlatformTransactionManager.class), FixedClock.at(AHORA), true).darBienvenidas(grupo);

        verify(delPrograma).enviarDelPrograma(chat, ana, ContenidoDelPrograma.texto("¡Ana, qué bueno tenerte! Carlos te acompaña."));
    }

    /** Un aprendiz nuevo recibe su bienvenida; con el almacenamiento de marcador sale solo el formal (G-5). */
    private String formalQueRecibe(BienvenidaEnSoporteService soporte, String nombreCompleto) {
        UserId aprendiz = UserId.of(UUID.randomUUID());
        when(usuarios.findById(aprendiz)).thenReturn(Optional.of(
                new UserSummary(aprendiz, nombreCompleto, null, UserRole.TRAINEE, UserStatus.ACTIVE)));

        soporte.darBienvenida(SOPORTE, aprendiz);

        ArgumentCaptor<ContenidoDelPrograma> enviado = ArgumentCaptor.forClass(ContenidoDelPrograma.class);
        verify(delPrograma).enviarDelPrograma(eq(SOPORTE), eq(aprendiz), enviado.capture());
        return enviado.getValue().texto();
    }

    private BienvenidaEnSoporteService servicioDelSoporte() {
        AlmacenamientoPort sinAlmacenamiento = mock(AlmacenamientoPort.class);
        when(sinAlmacenamiento.guardaObjetos()).thenReturn(false);
        return new BienvenidaEnSoporteService(mock(DibujarBienvenidaPort.class), sinAlmacenamiento, delPrograma, usuarios,
                mock(MarcaDeBienvenidaPort.class), textos, UUID::randomUUID, mock(PlatformTransactionManager.class), true);
    }
}
