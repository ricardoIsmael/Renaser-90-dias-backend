package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.in.conversacion.AutorizarAccesoAConversacionUseCase;
import com.renaser.os.chat.application.ports.in.conversacion.VerFotoDelSoporteUseCase.FotoDelSoporte;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort;
import com.renaser.os.chat.application.ports.out.bienvenida.TarjetaConNombrePort.TarjetaConNombre;
import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
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

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** La foto del chat de soporte (D-205): la tarjeta con el primer nombre del aprendiz dueño. */
@ExtendWith(MockitoExtension.class)
class FotoDelSoporteServiceTest {

    private static final Instant AHORA = Instant.parse("2026-09-27T15:00:00Z");
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId KELIN = UserId.of(UUID.randomUUID());
    private static final ConversacionId SOPORTE = ConversacionId.of(UUID.randomUUID());
    private static final ConversacionId GLOBAL = ConversacionId.of(UUID.randomUUID());
    private static final TarjetaConNombre TARJETA = new TarjetaConNombre(new byte[] {(byte) 0xFF, (byte) 0xD8}, "abc123");

    @Mock
    private LoadConversacionPort loadConversacionPort;
    @Mock
    private AutorizarAccesoAConversacionUseCase autorizarAcceso;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private TarjetaConNombrePort tarjetaPort;

    private FotoDelSoporteService servicio;

    @BeforeEach
    void preparar() {
        servicio = new FotoDelSoporteService(loadConversacionPort, autorizarAcceso, userSummaryFinder, tarjetaPort);
        lenient().when(loadConversacionPort.porId(SOPORTE)).thenReturn(Optional.of(
                Conversacion.crearSoporte(SOPORTE, ANA, "Ana - Formación Renaser", AHORA)));
        lenient().when(loadConversacionPort.porId(GLOBAL)).thenReturn(Optional.of(Conversacion.crearGlobal(GLOBAL, AHORA)));
        cuenta(ANA, "maría josé ñahui", UserRole.TRAINEE, UserStatus.ACTIVE);
        cuenta(KELIN, "Kelin Rojas", UserRole.ADMIN, UserStatus.ACTIVE);
        lenient().when(tarjetaPort.tarjetaDe(any())).thenReturn(TARJETA);
    }

    @Test
    @DisplayName("la aprendiz ve SU tarjeta: con su primer nombre, y la huella para el ETag")
    void laAprendizVeSuTarjeta() {
        when(autorizarAcceso.puedeVer(SOPORTE, ANA)).thenReturn(true);

        FotoDelSoporte foto = servicio.foto(ANA, SOPORTE);

        verify(tarjetaPort).tarjetaDe("María");
        assertThat(foto.jpeg()).isSameAs(TARJETA.jpeg());
        assertThat(foto.huella()).isEqualTo("abc123");
    }

    @Test
    @DisplayName("el staff del soporte ve la tarjeta de la aprendiz, no la suya")
    void elStaffVeLaTarjetaDeLaAprendiz() {
        when(autorizarAcceso.puedeVer(SOPORTE, KELIN)).thenReturn(true);

        servicio.foto(KELIN, SOPORTE);

        verify(tarjetaPort).tarjetaDe("María");
    }

    @Test
    @DisplayName("quien no puede ver la conversación recibe 403 y no se dibuja nada")
    void sinAccesoEs403() {
        when(autorizarAcceso.puedeVer(SOPORTE, KELIN)).thenReturn(false);

        assertThatThrownBy(() -> servicio.foto(KELIN, SOPORTE)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(tarjetaPort);
    }

    @Test
    @DisplayName("un grupo o la comunidad no tienen foto propia (404), aunque se pueda verlos")
    void loQueNoEsUnSoporteEs404() {
        when(autorizarAcceso.puedeVer(GLOBAL, ANA)).thenReturn(true);

        assertThatThrownBy(() -> servicio.foto(ANA, GLOBAL)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(tarjetaPort);
    }

    @Test
    @DisplayName("una conversación que no existe es 404, antes de preguntar el acceso")
    void laQueNoExisteEs404() {
        ConversacionId inexistente = ConversacionId.of(UUID.randomUUID());
        when(loadConversacionPort.porId(inexistente)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicio.foto(ANA, inexistente)).isInstanceOf(NoSuchElementException.class);
        verifyNoInteractions(autorizarAcceso, tarjetaPort);
    }

    @Test
    @DisplayName("una cuenta suspendida recibe 403")
    void suspendidaEs403() {
        cuenta(KELIN, "Kelin Rojas", UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.foto(KELIN, SOPORTE)).isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(tarjetaPort);
    }

    @Test
    @DisplayName("si la cuenta de la aprendiz ya no existe, la tarjeta va sin nombre")
    void sinLaCuentaDeLaAprendizVaSinNombre() {
        when(userSummaryFinder.findById(ANA)).thenReturn(Optional.empty());
        when(autorizarAcceso.puedeVer(SOPORTE, KELIN)).thenReturn(true);

        servicio.foto(KELIN, SOPORTE);

        verify(tarjetaPort).tarjetaDe("");
    }

    private void cuenta(UserId id, String nombre, UserRole rol, UserStatus estado) {
        lenient().when(userSummaryFinder.findById(id)).thenReturn(Optional.of(new UserSummary(id, nombre, null, rol, estado)));
    }
}
