package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.EnvioEmailFallidoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase;
import com.renaser.os.users.application.ports.in.user.InviteAndCreateUserUseCase.InviteStaffCommand;
import com.renaser.os.users.application.ports.out.autenticacion.EnviarEmailPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;

/**
 * Prueba de la correccion de C-11 (docs/informes/auditoria-seguridad-concurrencia-2026-09-01.html)
 * contra Postgres real (Testcontainers) -- mismo criterio que
 * {@code ProcesarValidacionV90ServiceTransaccionIT} (C-1): se autowirea el caso de uso por su
 * interfaz publica (bean real, con el proxy {@code @Transactional} de Spring) para que
 * {@link TransactionSynchronizationManager#isActualTransactionActive()} distinga de verdad el
 * codigo viejo (SMTP dentro de la transaccion) del nuevo (SMTP en {@code afterCommit}). Instanciar
 * {@code UserAccountService} con {@code new} en un test unitario no probaria nada: sin el proxy de
 * Spring, ninguna version de {@code @Transactional} tendria efecto.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class UserAccountServiceInvitarStaffTransaccionIT {

    @Autowired
    private InviteAndCreateUserUseCase inviteAndCreateUserUseCase;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockitoBean
    private EnviarEmailPort enviarEmailPort;

    private UserId actorId;

    /** Un ADMIN activo: es quien puede invitar staff (regla de dominio en {@code User.invite}). */
    @BeforeEach
    void seedAdmin() {
        actorId = UserId.of(UUID.randomUUID());
        jdbcTemplate.update("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (?, ?, 'Admin Fixture', CAST('ADMIN' AS renaser.rol_usuario), 'ACTIVO')
                        """,
                actorId.value(), actorId + "@renaser.test");
    }

    /** ON DELETE CASCADE arrastra perfiles_mentor y cualquier otra fila dependiente del usuario. */
    @AfterEach
    void limpiar() {
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", actorId.value());
    }

    @Test
    @DisplayName("C-11: el correo de invitacion de staff se manda SIN ninguna transaccion Spring activa")
    void elEnvioDeCorreoOcurreFueraDeLaTransaccion() {
        AtomicBoolean transaccionActivaAlEnviar = new AtomicBoolean(true);
        doAnswer(inv -> {
            transaccionActivaAlEnviar.set(TransactionSynchronizationManager.isActualTransactionActive());
            return null;
        }).when(enviarEmailPort).enviarInvitacionStaff(any(), any());
        String email = "staff-" + UUID.randomUUID() + "@renaser.test";

        UserId nuevoId = inviteAndCreateUserUseCase.inviteStaff(
                new InviteStaffCommand(email, "Nuevo Staff", UserRole.MENTOR, actorId));

        assertThat(transaccionActivaAlEnviar).as("el envio no debe correr con una transaccion abierta").isFalse();
        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", nuevoId.value());
    }

    @Test
    @DisplayName("C-11: si el envio de correo falla, la invitacion ya quedo confirmada -- usuario y "
            + "credencial persisten y la excepcion no se propaga al llamador")
    void unFalloDeEnvioNoPierdeLaInvitacion() {
        doThrow(new EnvioEmailFallidoException(new RuntimeException("SMTP no responde")))
                .when(enviarEmailPort).enviarInvitacionStaff(any(), any());
        String email = "staff-fallo-" + UUID.randomUUID() + "@renaser.test";

        UserId nuevoId = inviteAndCreateUserUseCase.inviteStaff(
                new InviteStaffCommand(email, "Nuevo Staff", UserRole.MENTOR, actorId));

        assertThat(nuevoId).isNotNull();
        String hash = jdbcTemplate.queryForObject(
                "SELECT hash_contrasena FROM renaser.usuarios WHERE id = ?", String.class, nuevoId.value());
        assertThat(hash).as("la credencial temporal quedo guardada pese al fallo de envio").isNotNull();

        jdbcTemplate.update("DELETE FROM renaser.usuarios WHERE id = ?", nuevoId.value());
    }
}
