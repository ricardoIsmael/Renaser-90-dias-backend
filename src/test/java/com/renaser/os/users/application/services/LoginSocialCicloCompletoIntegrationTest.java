package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase.ApproveAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.IniciarSesionConProveedorCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenVerificacionEmailPort;
import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.application.ports.out.user.SaveUserPort;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestId;
import com.renaser.os.users.domain.model.accountrequest.AccountRequestStatus;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import com.renaser.os.users.domain.model.user.Email;
import com.renaser.os.users.domain.model.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A-7 de punta a punta, contra Postgres real (Testcontainers) y no mocks: <b>alta social →
 * aprobacion de un ADMIN → el segundo toque del mismo proveedor devuelve sesion</b>.
 *
 * <p>Es la prueba que demuestra que el defecto murio, y tiene que ser de integracion: las tres
 * piezas que lo causaban viven en la base, no en el codigo. El {@code sub} del proveedor se
 * guarda en las columnas que agrego la migracion V12 ({@code solicitudes_cuenta.proveedor} /
 * {@code .sujeto_proveedor}), sobrevive ahi la espera entre el alta y la aprobacion, y recien al
 * aprobar se escribe la fila de {@code identidades_externas} — cuya FK exige que el usuario ya
 * exista, que es justamente el motivo por el que el vinculo no podia crearse antes. Con mocks se
 * probaria que los metodos se llaman entre si; con Postgres se prueba que el dato sobrevive.
 *
 * <p><b>El sintoma que cerraba el circulo:</b> antes de esto, el segundo "Continuar con Google"
 * no encontraba vinculo, intentaba dar de alta otra vez y chocaba con el usuario ya existente,
 * respondiendo "inicia sesion con tu metodo actual" — un metodo que esa persona no tiene, porque
 * el alta social deja {@code usuarios.hash_contrasena} en NULL a proposito. Quien se registraba
 * con Google o Apple no podia volver a entrar nunca.
 *
 * <p>La prueba que ya existia ({@code AutenticacionSocialServiceTest#identidadYaVinculada...})
 * <b>no</b> cubre esto: parte de un vinculo ya existente en un mock, o sea da por cierto
 * exactamente lo que el defecto impedia que ocurriera.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class LoginSocialCicloCompletoIntegrationTest {

    private static final String SUJETO_GOOGLE = "google-sub-ciclo-completo";

    @Autowired
    private LoadIdentidadExternaPort loadIdentidadExternaPort;
    @Autowired
    private LoadAccountRequestPort loadAccountRequestPort;
    @Autowired
    private LoadUserPort loadUserPort;
    @Autowired
    private SaveUserPort saveUserPort;
    @Autowired
    private SubmitAccountRequestUseCase submitAccountRequestUseCase;
    @Autowired
    private ApproveAccountRequestUseCase approveAccountRequestUseCase;
    @Autowired
    private TokenVerificacionEmailPort tokenVerificacionEmailPort;

    @Test
    void altaSocialAprobadaPermiteVolverAEntrarPorElMismoProveedor() {
        String email = "ciclo-" + UUID.randomUUID() + "@renaser.dev";
        var servicio = servicioConVerificadorQueDevuelve(email);
        UserId adminId = persistirAdmin();

        // 1) Primer toque en "Continuar con Google": no hay vinculo ni solicitud previa, se abre
        //    el alta guardando el (proveedor, sujeto) — el dato que antes se perdia aca mismo.
        ResultadoLoginSocial primerToque = servicio.iniciarSesion(comando());

        assertThat(primerToque).isInstanceOf(ResultadoLoginSocial.SolicitudCreada.class);
        AccountRequestId solicitudId = ((ResultadoLoginSocial.SolicitudCreada) primerToque).solicitudId();
        var solicitud = loadAccountRequestPort.byId(solicitudId).orElseThrow();
        assertThat(solicitud.origenSocial().proveedor()).isEqualTo(ProveedorIdentidad.GOOGLE);
        assertThat(solicitud.origenSocial().sujetoProveedor()).isEqualTo(SUJETO_GOOGLE);
        // Todavia no hay vinculo: la FK de identidades_externas exige un usuario aprobado.
        assertThat(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, SUJETO_GOOGLE))
                .isEmpty();

        // 1.bis) Mientras el admin no decide, volver a tocar el boton no es un error ni abre otra
        //        solicitud: es la variante SolicitudEnRevision que antes colapsaba en un 409.
        assertThat(servicio.iniciarSesion(comando()))
                .isInstanceOf(ResultadoLoginSocial.SolicitudEnRevision.class);

        // 2) Un ADMIN aprueba. Es aca donde approve() escribe la IdentidadExterna, en la misma
        //    transaccion que activa al usuario.
        approveAccountRequestUseCase.approve(new ApproveAccountRequestCommand(solicitudId, adminId));

        UserId usuarioAprobado = loadAccountRequestPort.byId(solicitudId).orElseThrow().usuarioId();
        assertThat(loadAccountRequestPort.byId(solicitudId).orElseThrow().status())
                .isEqualTo(AccountRequestStatus.APPROVED);
        assertThat(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, SUJETO_GOOGLE))
                .isPresent()
                .get()
                .extracting(identidad -> identidad.usuarioId())
                .isEqualTo(usuarioAprobado);

        // 3) Segundo toque del mismo proveedor con el mismo sub: sesion, no error. Este assert es
        //    literalmente el defecto A-7 — antes no llegaba nunca aca.
        ResultadoLoginSocial segundoToque = servicio.iniciarSesion(comando());

        assertThat(segundoToque).isInstanceOf(ResultadoLoginSocial.SesionIniciada.class);
        User usuario = ((ResultadoLoginSocial.SesionIniciada) segundoToque).usuario();
        assertThat(usuario.id()).isEqualTo(usuarioAprobado);
        assertThat(usuario.email()).isEqualTo(new Email(email));
        assertThat(usuario.status()).isEqualTo(UserStatus.ACTIVE);
    }

    /**
     * Se arma a mano en vez de autowirearlo porque el unico colaborador que no puede ser real es
     * el verificador: canjear el {@code code} contra Google exige red y un cliente OAuth. Todo lo
     * demas — los puertos de persistencia, el caso de uso de alta, el token de verificacion en
     * Redis — es el bean de produccion, que es de lo que trata esta prueba.
     */
    private AutenticacionSocialService servicioConVerificadorQueDevuelve(String email) {
        VerificadorIdentidadProveedor verificador = new VerificadorGoogleDeMentira(email);
        return new AutenticacionSocialService(List.of(verificador), loadIdentidadExternaPort,
                loadAccountRequestPort, loadUserPort, submitAccountRequestUseCase, tokenVerificacionEmailPort);
    }

    private UserId persistirAdmin() {
        UserId adminId = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.rehydrate(adminId, new Email("admin-" + UUID.randomUUID() + "@renaser.dev"),
                UserRole.ADMIN, UserStatus.ACTIVE, "Admin Que Aprueba", null, null, null, null));
        return adminId;
    }

    /** El telefono viaja en la llamada porque Google no lo devuelve (A-8, todavia abierto). */
    private static IniciarSesionConProveedorCommand comando() {
        return new IniciarSesionConProveedorCommand(ProveedorIdentidad.GOOGLE, "un-code", "un-verifier",
                "https://app.renaser.dev/callback", "+54 341 1234567", "Rosario", "203.0.113.7");
    }

    /** Devuelve siempre la misma identidad: es lo que representa "la misma persona volviendo". */
    private record VerificadorGoogleDeMentira(String email) implements VerificadorIdentidadProveedor {

        @Override
        public ProveedorIdentidad proveedor() {
            return ProveedorIdentidad.GOOGLE;
        }

        @Override
        public IdentidadVerificada verificar(CanjeCodigoCommand command) {
            return new IdentidadVerificada(SUJETO_GOOGLE, email, true, "Sofia Social");
        }
    }
}
