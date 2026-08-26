package com.renaser.os.users.infrastructure.adapter.out.persistence.credencial;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort;
import com.renaser.os.users.application.ports.out.autenticacion.LoadCredencialPort.CredencialParaLogin;
import com.renaser.os.users.application.ports.out.autenticacion.SaveCredencialPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.user.Credencial;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class CredencialPersistenceAdapterTest {

    @Autowired
    private SaveUserPort saveUserPort;
    @Autowired
    private LoadCredencialPort loadCredencialPort;
    @Autowired
    private SaveCredencialPort saveCredencialPort;

    @Test
    void unUsuarioSinContrasenaTodaviaTieneHashNulo() {
        UserId id = crearUsuario("solo-social@renaser.dev");

        CredencialParaLogin credencial = loadCredencialPort.porEmail("solo-social@renaser.dev").orElseThrow();

        assertThat(credencial.usuarioId()).isEqualTo(id);
        assertThat(credencial.hash()).isNull();
        assertThat(credencial.permiteLoginPorContrasena()).isFalse();
        assertThat(credencial.cuentaHabilitada()).isTrue();
    }

    @Test
    void guardarYLeerElHashPersisteEnLaMismaFilaDeUsuarios() {
        UserId id = crearUsuario("con-contrasena@renaser.dev");

        saveCredencialPort.guardar(id, new Credencial("{bcrypt}$2a$10$abcdefghijklmnopqrstuv", Instant.now()));

        CredencialParaLogin credencial = loadCredencialPort.porEmail("con-contrasena@renaser.dev").orElseThrow();
        assertThat(credencial.hash()).isEqualTo("{bcrypt}$2a$10$abcdefghijklmnopqrstuv");
        assertThat(credencial.permiteLoginPorContrasena()).isTrue();
    }

    @Test
    void guardarSobreUnUsuarioInexistenteFalla() {
        assertThatThrownBy(() -> saveCredencialPort.guardar(UserId.of(UUID.randomUUID()),
                new Credencial("{bcrypt}$2a$10$abcdefghijklmnopqrstuv", Instant.now())))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unEmailQueNoExisteDevuelveOptionalVacio() {
        Optional<CredencialParaLogin> credencial = loadCredencialPort.porEmail("nadie@renaser.dev");

        assertThat(credencial).isEmpty();
    }

    /**
     * El puerto traduce el estado a un booleano fail-closed (docs/MODULO_AUTH.md §2, R-3): esto
     * NO reproduce una fila INACTIVO real (el dominio no puede construirla), pero confirma el
     * camino que si se ejercita: ACTIVO -> habilitada. La traduccion de INACTIVO queda cubierta
     * por el diseno del propio adaptador (comparacion de string), no por este test.
     */
    @Test
    void unaCuentaActivaEstaHabilitada() {
        crearUsuario("activo@renaser.dev");

        CredencialParaLogin credencial = loadCredencialPort.porEmail("activo@renaser.dev").orElseThrow();

        assertThat(credencial.cuentaHabilitada()).isTrue();
    }

    private UserId crearUsuario(String email) {
        UserId id = UserId.of(UUID.randomUUID());
        User usuario = User.rehydrate(id, new Email(email), UserRole.TRAINEE, UserStatus.ACTIVE, "Actor de Prueba",
                null, null, null, null);
        saveUserPort.save(usuario);
        return id;
    }
}
