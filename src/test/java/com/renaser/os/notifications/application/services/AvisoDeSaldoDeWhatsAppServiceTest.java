package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.out.whatsapp.FallasDeSaldoDeWhatsAppPort;
import com.renaser.os.notifications.domain.model.whatsapp.AvisoDeSaldoDeWhatsApp;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** El aviso de saldo de WhatsApp: quién lo ve (spec 054, FR-004) y qué ventana se mira (FR-001). */
class AvisoDeSaldoDeWhatsAppServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-16T15:00:00Z");

    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final List<Instant> desdeConsultados = new ArrayList<>();
    private List<Instant> fallas = List.of();
    private final FallasDeSaldoDeWhatsAppPort puerto = new FallasDeSaldoDeWhatsAppPort() {
        @Override
        public List<Instant> fallasPorSaldoDesde(Instant desde, String patronDelMotivo) {
            desdeConsultados.add(desde);
            assertThat(patronDelMotivo).isEqualTo(AvisoDeSaldoDeWhatsApp.PATRON_DEL_MOTIVO);
            return fallas;
        }

        @Override
        public Optional<Instant> ultimoQueSalioDesde(Instant desde) {
            return Optional.empty();
        }
    };
    private final AvisoDeSaldoDeWhatsAppService servicio = new AvisoDeSaldoDeWhatsAppService(usuarios, puerto, new Clock() {
        @Override
        public Instant now() {
            return AHORA;
        }

        @Override
        public LocalDate today() {
            return LocalDate.of(2026, 11, 16);
        }
    });

    private UserId actor(UserRole rol, UserStatus estado) {
        UserId id = UserId.of(UUID.randomUUID());
        when(usuarios.findById(id)).thenReturn(Optional.of(new UserSummary(id, "Quien Sea", null, rol, estado)));
        return id;
    }

    // spec 054 FR-001, FR-004
    @Test
    @DisplayName("Administración y Alquimista ven el aviso, y se mira solo hacia las últimas 24 horas")
    void loVenAdminYAlquimista() {
        fallas = List.of(AHORA.minusSeconds(3600));
        for (UserRole rol : new UserRole[] {UserRole.ADMIN, UserRole.ALCHEMIST}) {
            assertThat(servicio.ver(actor(rol, UserStatus.ACTIVE)).avisar()).as(rol.name()).isTrue();
        }
        assertThat(desdeConsultados).containsOnly(AHORA.minusSeconds(24 * 3600));
    }

    // spec 054 FR-004
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "ALCHEMIST"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("cualquier otro rol recibe 403")
    void losDemasNoLoVen(UserRole rol) {
        UserId quien = actor(rol, UserStatus.ACTIVE);

        assertThatThrownBy(() -> servicio.ver(quien)).isInstanceOf(NotAuthorizedException.class);
    }

    // spec 054 FR-004
    @Test
    @DisplayName("una cuenta de Administración suspendida recibe 403")
    void suspendidoNoLoVe() {
        UserId quien = actor(UserRole.ADMIN, UserStatus.SUSPENDED);

        assertThatThrownBy(() -> servicio.ver(quien)).isInstanceOf(NotAuthorizedException.class);
    }
}
