package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.whatsapp.VerResumenDeWhatsAppDelEventoUseCase.ResumenDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.EntregaDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El resumen de conteos del WhatsApp de un evento (spec 041, FR-029, SC-013). Solo Administracion y Alquimista; solo conteos. */
class ResumenDeWhatsAppDelEventoServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-07T00:00:00Z");

    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final EntregasDeWhatsAppEnMemoria entregas = new EntregasDeWhatsAppEnMemoria();
    private final UUID evento = UUID.randomUUID();
    private ResumenDeWhatsAppDelEventoService servicio;

    @BeforeEach
    void armar() {
        servicio = new ResumenDeWhatsAppDelEventoService(usuarios, entregas);
    }

    private UserId actor(UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Quien Sea", null, rol, estado)));
        return id;
    }

    private void entrega(EstadoDeEntrega estado, String motivo, UserId persona) {
        entregas.apartar(new EntregaDeWhatsApp(UUID.randomUUID(), UUID.randomUUID(), evento, persona, 10, estado, motivo, null, null, AHORA, AHORA));
    }

    // spec 041 FR-029
    @Test
    @DisplayName("cuenta los enviados, entregados, leidos, fallados y las personas sin numero; sin nombres ni textos")
    void losConteos() {
        UserId a = UserId.of(UUID.randomUUID());
        UserId b = UserId.of(UUID.randomUUID());
        entrega(EstadoDeEntrega.ACEPTADO, null, a);
        entrega(EstadoDeEntrega.ENVIADO, null, a);
        entrega(EstadoDeEntrega.ENTREGADO, null, a);
        entrega(EstadoDeEntrega.LEIDO, null, a);
        entrega(EstadoDeEntrega.FALLIDO, "Template does not exist", a);
        entrega(EstadoDeEntrega.NO_SE_SUPO, "YCloud respondió 503", a);
        entrega(EstadoDeEntrega.OMITIDO, "Sin WhatsApp", b);
        entrega(EstadoDeEntrega.OMITIDO, "Sin WhatsApp", b);
        entrega(EstadoDeEntrega.OMITIDO, "Llegó tarde", a);

        ResumenDeWhatsApp resumen = servicio.resumen(actor(UserRole.ADMIN, UserStatus.ACTIVE), evento);

        assertThat(resumen.enviados()).as("aceptados + enviados + entregados + leidos").isEqualTo(4);
        assertThat(resumen.entregados()).isEqualTo(2);
        assertThat(resumen.leidos()).isEqualTo(1);
        assertThat(resumen.fallaron()).isEqualTo(1);
        assertThat(resumen.noSeSupo()).isEqualTo(1);
        assertThat(resumen.sinNumero()).as("personas, no mensajes").isEqualTo(1);
        assertThat(resumen.omitidos()).as("los omitidos por otro motivo").isEqualTo(1);
    }

    // spec 041 FR-029
    @Test
    @DisplayName("el resumen de otro evento no se mezcla")
    void otroEvento() {
        entrega(EstadoDeEntrega.ENTREGADO, null, UserId.of(UUID.randomUUID()));

        assertThat(servicio.resumen(actor(UserRole.ALCHEMIST, UserStatus.ACTIVE), UUID.randomUUID()).enviados()).isZero();
    }

    // spec 041 FR-029, SC-013
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE", "MENTOR_LEAD", "TRAINEE", "IT_SUPPORT"})
    @DisplayName("mentor, guia, lider, aprendiz y Soporte TI reciben 403")
    void losDemasRolesNoLoVen(UserRole rol) {
        UserId quien = actor(rol, UserStatus.ACTIVE);

        assertThatThrownBy(() -> servicio.resumen(quien, evento)).isInstanceOf(NotAuthorizedException.class);
    }

    // spec 041 FR-029, SC-013
    @Test
    @DisplayName("un administrador suspendido tambien recibe 403")
    void suspendido() {
        UserId suspendido = actor(UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.resumen(suspendido, evento)).isInstanceOf(NotAuthorizedException.class);
    }

    // spec 041 FR-029
    @Test
    @DisplayName("el resumen no tiene ningun campo que pueda llevar un nombre, un texto o un numero")
    void soloConteos() {
        var campos = java.util.Arrays.stream(ResumenDeWhatsApp.class.getRecordComponents()).map(java.lang.reflect.RecordComponent::getType).toList();

        assertThat(campos).containsOnly(int.class);
    }
}
