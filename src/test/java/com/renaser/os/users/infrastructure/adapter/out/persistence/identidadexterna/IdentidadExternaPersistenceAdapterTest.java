package com.renaser.os.users.infrastructure.adapter.out.persistence.identidadexterna;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class IdentidadExternaPersistenceAdapterTest {

    @Autowired
    private SaveUserPort saveUserPort;
    @Autowired
    private LoadIdentidadExternaPort loadIdentidadExternaPort;
    @Autowired
    private SaveIdentidadExternaPort saveIdentidadExternaPort;

    @Test
    void guardarYLeerElVinculoPorProveedorYSujetoDevuelveElUsuarioCorrecto() {
        UserId id = crearUsuario("con-google@renaser.dev");
        Instant vinculadaEn = Instant.parse("2026-08-26T10:00:00Z");

        saveIdentidadExternaPort.guardar(new IdentidadExterna(ProveedorIdentidad.GOOGLE, "google-sub-1", id,
                "con-google@renaser.dev", vinculadaEn));

        IdentidadExterna vinculo = loadIdentidadExternaPort
                .porProveedorYSujeto(ProveedorIdentidad.GOOGLE, "google-sub-1").orElseThrow();
        assertThat(vinculo.usuarioId()).isEqualTo(id);
        assertThat(vinculo.emailProveedor()).isEqualTo("con-google@renaser.dev");
        assertThat(vinculo.vinculadaEn()).isEqualTo(vinculadaEn);
    }

    @Test
    void unSujetoQueNoExisteDevuelveOptionalVacio() {
        Optional<IdentidadExterna> vinculo = loadIdentidadExternaPort
                .porProveedorYSujeto(ProveedorIdentidad.GOOGLE, "no-existe");

        assertThat(vinculo).isEmpty();
    }

    /**
     * El mismo `sub` con proveedores distintos son dos filas distintas (la PK es
     * `(proveedor, sujeto_proveedor)`, no `sujeto_proveedor` solo) — confirma que no hay
     * colision cruzada entre proveedores aunque el `sub` coincidiera por casualidad.
     */
    @Test
    void elMismoSujetoEnDosProveedoresDistintosNoColisiona() {
        UserId id = crearUsuario("multi-proveedor@renaser.dev");
        saveIdentidadExternaPort.guardar(new IdentidadExterna(ProveedorIdentidad.GOOGLE, "sub-compartido", id,
                "multi-proveedor@renaser.dev", Instant.now()));

        Optional<IdentidadExterna> comoApple = loadIdentidadExternaPort
                .porProveedorYSujeto(ProveedorIdentidad.APPLE, "sub-compartido");

        assertThat(comoApple).isEmpty();
    }

    @Test
    void vincularElMismoProveedorYSujetoDosVecesFalla() {
        UserId primero = crearUsuario("primero@renaser.dev");
        UserId segundo = crearUsuario("segundo@renaser.dev");
        saveIdentidadExternaPort.guardar(new IdentidadExterna(ProveedorIdentidad.GOOGLE, "sub-disputado", primero,
                "primero@renaser.dev", Instant.now()));

        assertThatThrownBy(() -> saveIdentidadExternaPort.guardar(new IdentidadExterna(ProveedorIdentidad.GOOGLE,
                "sub-disputado", segundo, "segundo@renaser.dev", Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void vincularSinUsuarioExistenteFallaPorLaFk() {
        UserId usuarioInexistente = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> saveIdentidadExternaPort.guardar(new IdentidadExterna(ProveedorIdentidad.GOOGLE,
                "sub-huerfano", usuarioInexistente, null, Instant.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UserId crearUsuario(String email) {
        UserId id = UserId.of(UUID.randomUUID());
        User usuario = User.rehydrate(id, new Email(email), UserRole.TRAINEE, UserStatus.ACTIVE, "Actor de Prueba",
                null, null, null, null);
        saveUserPort.save(usuario);
        return id;
    }
}
