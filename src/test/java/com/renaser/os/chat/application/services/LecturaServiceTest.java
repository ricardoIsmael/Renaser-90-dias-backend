package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.lectura.MarcasDeLecturaPort;
import com.renaser.os.chat.application.ports.out.lectura.PublicarLecturaFanoutPort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * El aviso en vivo de la doble marca (D-208): qué sale por el canal de la conversación cuando alguien
 * lee, y cuándo no sale nada. Contra el código viejo no existía: marcar leído no avisaba a nadie.
 */
@ExtendWith(MockitoExtension.class)
class LecturaServiceTest {

    private static final Instant ESCRIBIO_ANA = Instant.parse("2026-09-27T00:00:00Z");
    private static final Instant LEYO_LUIS = Instant.parse("2026-09-27T00:10:00Z");

    private final UserId ana = UserId.of(UUID.randomUUID());
    private final UserId luis = UserId.of(UUID.randomUUID());
    private final UserId kelin = UserId.of(UUID.randomUUID());
    private final UserId rita = UserId.of(UUID.randomUUID());
    private final Map<UserId, UserSummary> cuentas = new LinkedHashMap<>();

    @Mock
    private MarcasDeLecturaPort marcasDeLecturaPort;
    @Mock
    private PublicarLecturaFanoutPort publicarLecturaFanoutPort;
    @Mock
    private UserSummaryFinder userSummaryFinder;

    private LecturaService servicio;

    @BeforeEach
    void setUp() {
        servicio = new LecturaService(marcasDeLecturaPort, publicarLecturaFanoutPort, userSummaryFinder);
        cuenta(ana, UserRole.TRAINEE, UserStatus.ACTIVE);
        cuenta(luis, UserRole.TRAINEE, UserStatus.ACTIVE);
        cuenta(kelin, UserRole.ADMIN, UserStatus.ACTIVE);
        cuenta(rita, UserRole.ADMIN, UserStatus.SUSPENDED);
        lenient().when(userSummaryFinder.findByIds(any())).thenAnswer(inv -> {
            Map<UserId, UserSummary> pedidas = new LinkedHashMap<>();
            for (UserId id : inv.<Collection<UserId>>getArgument(0)) {
                if (cuentas.containsKey(id)) {
                    pedidas.put(id, cuentas.get(id));
                }
            }
            return pedidas;
        });
    }

    private void cuenta(UserId id, UserRole rol, UserStatus estado) {
        cuentas.put(id, new UserSummary(id, "Persona", null, rol, estado));
    }

    private static Participante marca(Conversacion conversacion, UserId quien, Instant leyoHasta) {
        return Participante.rehydrate(conversacion.id(), quien, leyoHasta, ESCRIBIO_ANA.minusSeconds(3600));
    }

    private Conversacion directa() {
        return Conversacion.crearDirecta(ConversacionId.of(UUID.randomUUID()), Conversacion.claveDirectaDe(ana, luis),
                ESCRIBIO_ANA.minusSeconds(3600));
    }

    @Test
    @DisplayName("en un 1 a 1, al leer Luis sale «todos leyeron hasta» la marca más vieja de los dos")
    void enUnUnoAUnoAvisaHastaDondeLeyeronLosDos() {
        Conversacion directa = directa();
        when(marcasDeLecturaPort.participantesDe(directa.id()))
                .thenReturn(List.of(marca(directa, ana, ESCRIBIO_ANA), marca(directa, luis, LEYO_LUIS)));

        servicio.anunciar(directa);

        verify(publicarLecturaFanoutPort).publicarLectura(directa.id(), ESCRIBIO_ANA);
    }

    @Test
    @DisplayName("en la comunidad no se consulta ni se avisa nada: ahí no hay ✓✓")
    void enLaComunidadNoSeAvisa() {
        Conversacion comunidad = Conversacion.crearGlobal(ConversacionId.of(UUID.randomUUID()), ESCRIBIO_ANA);

        servicio.anunciar(comunidad);

        verifyNoInteractions(marcasDeLecturaPort, userSummaryFinder, publicarLecturaFanoutPort);
    }

    @Test
    @DisplayName("si alguien nunca abrió la conversación no sale aviso: nada está leído por todos")
    void sinAvisoSiAlguienNuncaLeyo() {
        Conversacion directa = directa();
        when(marcasDeLecturaPort.participantesDe(directa.id()))
                .thenReturn(List.of(marca(directa, ana, ESCRIBIO_ANA), marca(directa, luis, null)));

        servicio.anunciar(directa);

        verify(publicarLecturaFanoutPort, never()).publicarLectura(any(), any());
    }

    /**
     * Suspender no saca a nadie del chat: la fila queda, con su marca congelada. Si contara, ni este
     * soporte ni ninguno donde esté esa cuenta de staff llegaría nunca a ✓✓.
     */
    @Test
    @DisplayName("una cuenta suspendida no cuenta: su marca congelada no frena el ✓✓ del soporte")
    void unaCuentaSuspendidaNoFrenaElAviso() {
        Conversacion soporte = Conversacion.crearSoporte(ConversacionId.of(UUID.randomUUID()), ana,
                "Ana – Formación Renaser", ESCRIBIO_ANA.minusSeconds(3600));
        when(marcasDeLecturaPort.participantesDe(soporte.id())).thenReturn(List.of(
                marca(soporte, ana, ESCRIBIO_ANA), marca(soporte, kelin, LEYO_LUIS), marca(soporte, rita, null)));

        servicio.anunciar(soporte);

        verify(publicarLecturaFanoutPort).publicarLectura(soporte.id(), ESCRIBIO_ANA);
    }

    @Test
    @DisplayName("si del otro lado no queda nadie con la cuenta activa, no hay nada que avisar")
    void sinNadieActivoDelOtroLado() {
        Conversacion soporte = Conversacion.crearSoporte(ConversacionId.of(UUID.randomUUID()), ana,
                "Ana – Formación Renaser", ESCRIBIO_ANA.minusSeconds(3600));
        when(marcasDeLecturaPort.participantesDe(soporte.id()))
                .thenReturn(List.of(marca(soporte, ana, ESCRIBIO_ANA), marca(soporte, rita, LEYO_LUIS)));

        servicio.anunciar(soporte);

        verify(publicarLecturaFanoutPort, never()).publicarLectura(any(), any());
    }

    @Test
    @DisplayName("si leer las marcas falla, no falla hacia arriba: la lectura ya quedó guardada")
    void nuncaFallaHaciaArriba() {
        Conversacion directa = directa();
        when(marcasDeLecturaPort.participantesDe(directa.id())).thenThrow(new IllegalStateException("base caída"));

        assertThatNoException().isThrownBy(() -> servicio.anunciar(directa));
        verify(publicarLecturaFanoutPort, never()).publicarLectura(any(), any());
    }
}
