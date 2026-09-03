package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdentidadYaVinculadaException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.autenticacion.VincularIdentidadSocialUseCase.VincularIdentidadSocialCommand;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.SaveIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.identidadexterna.IdentidadExterna;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La vinculacion explicita de §6.9 contra Postgres real (Testcontainers), no mocks: lo que se
 * prueba aca es que la fila de {@code identidades_externas} <b>se escribe de verdad</b> y que la
 * frontera de "esa identidad ya tiene dueño" se sostiene contra el dato persistido, no contra un
 * {@code Optional} preparado a mano. Mismo criterio y misma forma que
 * {@link LoginSocialCicloCompletoIntegrationTest}.
 *
 * <p>Lo unico simulado es el verificador del proveedor: canjear el {@code code} contra Google
 * exige red y un OAuth client (A-9).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class VinculacionIdentidadSocialIntegrationTest {

    @Autowired
    private LoadIdentidadExternaPort loadIdentidadExternaPort;
    @Autowired
    private SaveIdentidadExternaPort saveIdentidadExternaPort;
    @Autowired
    private LoadUserPort loadUserPort;
    @Autowired
    private SaveUserPort saveUserPort;
    @Autowired
    private Clock clock;

    @Test
    void vinculaLaIdentidadALaCuentaDeLaSesionYEsIdempotente() {
        String sujeto = "google-sub-link-" + UUID.randomUUID();
        UserId duena = persistirUsuario();
        var servicio = servicioConVerificadorQueDevuelve(sujeto, "personal-" + UUID.randomUUID() + "@gmail.com");

        servicio.vincular(comando(duena, sujeto));

        var vinculo = loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, sujeto);
        assertThat(vinculo).isPresent();
        assertThat(vinculo.get().usuarioId()).isEqualTo(duena);
        Instant vinculadaEn = vinculo.get().vinculadaEn();

        // Segundo toque del mismo boton: no falla y no reescribe la fila (si intentara insertar
        // otra vez, la PK (proveedor, sujeto_proveedor) haria estallar la transaccion entera).
        servicio.vincular(comando(duena, sujeto));

        assertThat(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, sujeto))
                .get()
                .satisfies(persistido -> {
                    assertThat(persistido.usuarioId()).isEqualTo(duena);
                    assertThat(persistido.vinculadaEn()).isEqualTo(vinculadaEn);
                });
    }

    /**
     * El vector de apropiacion inverso al de §6.4, contra el dato real: la identidad ya tiene
     * dueño y otro usuario intenta colgarsela. Tiene que cortar con 409 y dejar el vinculo
     * original intacto.
     */
    @Test
    void unaIdentidadConDuenoNoSePuedeColgarDeOtraCuenta() {
        String sujeto = "google-sub-ajeno-" + UUID.randomUUID();
        UserId duena = persistirUsuario();
        UserId intruso = persistirUsuario();
        saveIdentidadExternaPort.guardar(IdentidadExterna.vincular(ProveedorIdentidad.GOOGLE, sujeto, duena,
                "personal@gmail.com", clock));
        var servicio = servicioConVerificadorQueDevuelve(sujeto, "personal@gmail.com");

        assertThatThrownBy(() -> servicio.vincular(comando(intruso, sujeto)))
                .isInstanceOf(IdentidadYaVinculadaException.class);

        assertThat(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, sujeto))
                .get()
                .extracting(IdentidadExterna::usuarioId)
                .isEqualTo(duena);
    }

    /**
     * Se arma a mano por el mismo motivo que en {@link LoginSocialCicloCompletoIntegrationTest}:
     * el unico colaborador que no puede ser real es el verificador del proveedor. Todo lo demas
     * — los puertos de persistencia, el guard de cuenta activa, el reloj — es el bean de
     * produccion, que es de lo que trata esta prueba.
     */
    private VinculacionIdentidadSocialService servicioConVerificadorQueDevuelve(String sujeto, String email) {
        return new VinculacionIdentidadSocialService(List.of(new VerificadorGoogleDeMentira(sujeto, email)),
                loadIdentidadExternaPort, saveIdentidadExternaPort, new RequireActiveUserGuard(loadUserPort), clock);
    }

    private UserId persistirUsuario() {
        UserId id = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.rehydrate(id, new Email("link-" + UUID.randomUUID() + "@renaser.dev"),
                UserRole.TRAINEE, UserStatus.ACTIVE, "Persona Que Vincula", null, null, null, null));
        return id;
    }

    private static VincularIdentidadSocialCommand comando(UserId actorId, String sujeto) {
        return new VincularIdentidadSocialCommand(actorId, ProveedorIdentidad.GOOGLE, "un-code-" + sujeto,
                "un-verifier", "https://app.renaser.dev/callback");
    }

    private record VerificadorGoogleDeMentira(String sujeto, String email) implements VerificadorIdentidadProveedor {

        @Override
        public ProveedorIdentidad proveedor() {
            return ProveedorIdentidad.GOOGLE;
        }

        @Override
        public IdentidadVerificada verificar(CanjeCodigoCommand command) {
            return new IdentidadVerificada(sujeto, email, true, "Persona Que Vincula");
        }
    }
}
