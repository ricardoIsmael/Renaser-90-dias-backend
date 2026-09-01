package com.renaser.os.users.application.services;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.accountrequest.ApproveAccountRequestUseCase.ApproveAccountRequestCommand;
import com.renaser.os.users.application.ports.in.accountrequest.SubmitAccountRequestUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase;
import com.renaser.os.users.application.ports.in.autenticacion.CompletarRegistroSocialUseCase.CompletarRegistroSocialCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.IniciarSesionConProveedorCommand;
import com.renaser.os.users.application.ports.in.autenticacion.IniciarSesionConProveedorUseCase.ResultadoLoginSocial;
import com.renaser.os.users.application.ports.out.accountrequest.LoadAccountRequestPort;
import com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand;
import com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada;
import com.renaser.os.users.application.ports.out.autenticacion.LoadIdentidadExternaPort;
import com.renaser.os.users.application.ports.out.autenticacion.TokenRegistroPendienteSocialPort;
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
 * A-7 y D-65 de punta a punta, contra Postgres real (Testcontainers) y no mocks: <b>alta social
 * en DOS pasos (identidad nueva → registro pendiente → confirmar formulario) → aprobacion de un
 * ADMIN → el segundo toque del mismo proveedor devuelve sesion</b>.
 *
 * <p>Es la prueba que demuestra que los dos defectos murieron, y tiene que ser de integracion:
 * las piezas que los causaban viven en la base (A-7) o en Redis (D-65), no solo en el codigo. El
 * {@code sub} del proveedor se guarda en las columnas que agrego la migracion V12
 * ({@code solicitudes_cuenta.proveedor} / {@code .sujeto_proveedor}), sobrevive ahi la espera
 * entre el alta y la aprobacion, y recien al aprobar se escribe la fila de
 * {@code identidades_externas} — cuya FK exige que el usuario ya exista, que es justamente el
 * motivo por el que el vinculo no podia crearse antes. Con mocks se probaria que los metodos se
 * llaman entre si; con Postgres y Redis reales se prueba que el dato sobrevive.
 *
 * <p><b>Lo que cambio con D-65 (2026-09-01, docs/MODULO_AUTH.md §6.10):</b> antes,
 * {@code servicio.iniciarSesion(comando())} para una identidad nueva devolvia directamente
 * {@code SolicitudCreada} — la AccountRequest se abria en la misma llamada que verificaba la
 * identidad. Ahora devuelve {@code RegistroPendiente} (la identidad queda retenida en Redis con
 * un token de un solo uso) y hace falta un segundo paso,
 * {@code completarRegistroSocialUseCase.completar(...)}, para recien ahi abrir la solicitud. El
 * motivo: el {@code code} de OAuth es de un solo uso y la app no conoce el correo/nombre hasta
 * canjearlo, asi que no podia prellenar un formulario de confirmacion sin retener la identidad
 * en algun lado.
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
    private TokenRegistroPendienteSocialPort tokenRegistroPendienteSocialPort;
    @Autowired
    private CompletarRegistroSocialUseCase completarRegistroSocialUseCase;

    @Test
    void altaSocialAprobadaPermiteVolverAEntrarPorElMismoProveedor() {
        String email = "ciclo-" + UUID.randomUUID() + "@renaser.dev";
        var servicio = servicioConVerificadorQueDevuelve(email);
        UserId adminId = persistirAdmin();

        // 1) Primer toque en "Continuar con Google": no hay vinculo ni solicitud previa, se
        //    retiene la identidad en Redis (D-65) — todavia NO hay AccountRequest.
        ResultadoLoginSocial primerToque = servicio.iniciarSesion(comando());

        assertThat(primerToque).isInstanceOf(ResultadoLoginSocial.RegistroPendiente.class);
        ResultadoLoginSocial.RegistroPendiente pendiente = (ResultadoLoginSocial.RegistroPendiente) primerToque;
        assertThat(pendiente.email()).isEqualTo(email);

        // 1.bis) La persona confirma el formulario de la app: recien ACA se abre la AccountRequest,
        //        con el (proveedor, sujeto) que quedo retenido en Redis desde el primer toque.
        AccountRequestId solicitudId = completarRegistroSocialUseCase.completar(new CompletarRegistroSocialCommand(
                pendiente.token(), "Sofia Confirmada", "+54 341 1234567", "Rosario", "203.0.113.7"));
        var solicitud = loadAccountRequestPort.byId(solicitudId).orElseThrow();
        assertThat(solicitud.origenSocial().proveedor()).isEqualTo(ProveedorIdentidad.GOOGLE);
        assertThat(solicitud.origenSocial().sujetoProveedor()).isEqualTo(SUJETO_GOOGLE);
        // Todavia no hay vinculo: la FK de identidades_externas exige un usuario aprobado.
        assertThat(loadIdentidadExternaPort.porProveedorYSujeto(ProveedorIdentidad.GOOGLE, SUJETO_GOOGLE))
                .isEmpty();

        // 1.ter) Mientras el admin no decide, volver a tocar el boton no reabre otro registro
        //        pendiente: es la variante SolicitudEnRevision.
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

        // 3) Segundo toque del mismo proveedor con el mismo sub: sesion, no error.
        ResultadoLoginSocial segundoToque = servicio.iniciarSesion(comando());

        assertThat(segundoToque).isInstanceOf(ResultadoLoginSocial.SesionIniciada.class);
        User usuario = ((ResultadoLoginSocial.SesionIniciada) segundoToque).usuario();
        assertThat(usuario.id()).isEqualTo(usuarioAprobado);
        assertThat(usuario.email()).isEqualTo(new Email(email));
        assertThat(usuario.status()).isEqualTo(UserStatus.ACTIVE);
    }

    /**
     * D-61 (2026-09-01) + D-65: el alta por Google <b>sin telefono</b>, de punta a punta contra
     * Postgres real, ahora en dos pasos. Es el flujo que estaba muerto antes de D-61:
     * {@code AutenticacionSocialService} exigia telefono, Google no lo devuelve y el boton
     * social no lo mandaba, asi que toda cuenta nueva por Google recibia 400.
     *
     * <p>Tiene que ser de integracion por la misma razon que la prueba de arriba: el bloqueo
     * tambien vivia en la base ({@code solicitudes_cuenta.telefono NOT NULL} del baseline V1). Con
     * mocks se probaria que el servicio ya no tira la excepcion; con Postgres se prueba que la
     * fila entra y que la aprobacion sigue funcionando con la columna vacia.
     */
    @Test
    void altaSocialPorGoogleSinTelefonoSeCompletaYSeAprueba() {
        String email = "sin-telefono-" + UUID.randomUUID() + "@renaser.dev";
        var servicio = servicioConVerificadorQueDevuelve(email);
        UserId adminId = persistirAdmin();

        ResultadoLoginSocial primerToque = servicio.iniciarSesion(comando());
        assertThat(primerToque).isInstanceOf(ResultadoLoginSocial.RegistroPendiente.class);
        String token = ((ResultadoLoginSocial.RegistroPendiente) primerToque).token();

        // Lo que manda de verdad el formulario de confirmacion cuando Google no devolvio
        // telefono y la persona no lo completo: null es un estado valido (D-61).
        AccountRequestId solicitudId = completarRegistroSocialUseCase.completar(new CompletarRegistroSocialCommand(
                token, "Sin Telefono", null, null, "203.0.113.7"));
        var solicitud = loadAccountRequestPort.byId(solicitudId).orElseThrow();
        assertThat(solicitud.phone()).isNull();
        assertThat(solicitud.origenSocial().sujetoProveedor()).isEqualTo(SUJETO_GOOGLE);

        // La solicitud sin telefono se aprueba igual y deja al usuario ACTIVE con su vinculo.
        approveAccountRequestUseCase.approve(new ApproveAccountRequestCommand(solicitudId, adminId));

        assertThat(loadAccountRequestPort.byId(solicitudId).orElseThrow().status())
                .isEqualTo(AccountRequestStatus.APPROVED);
        ResultadoLoginSocial segundoToque = servicio.iniciarSesion(comando());
        assertThat(segundoToque).isInstanceOf(ResultadoLoginSocial.SesionIniciada.class);
        assertThat(((ResultadoLoginSocial.SesionIniciada) segundoToque).usuario().email())
                .isEqualTo(new Email(email));
    }

    /**
     * Se arma a mano en vez de autowirearlo porque el unico colaborador que no puede ser real es
     * el verificador: canjear el {@code code} contra Google exige red y un cliente OAuth. Todo lo
     * demas — los puertos de persistencia, el registro pendiente en Redis — es el bean de
     * produccion, que es de lo que trata esta prueba.
     */
    private AutenticacionSocialService servicioConVerificadorQueDevuelve(String email) {
        VerificadorIdentidadProveedor verificador = new VerificadorGoogleDeMentira(email);
        return new AutenticacionSocialService(List.of(verificador), loadIdentidadExternaPort,
                loadAccountRequestPort, loadUserPort, tokenRegistroPendienteSocialPort);
    }

    private UserId persistirAdmin() {
        UserId adminId = UserId.of(UUID.randomUUID());
        saveUserPort.save(User.rehydrate(adminId, new Email("admin-" + UUID.randomUUID() + "@renaser.dev"),
                UserRole.ADMIN, UserStatus.ACTIVE, "Admin Que Aprueba", null, null, null, null));
        return adminId;
    }

    private static IniciarSesionConProveedorCommand comando() {
        return new IniciarSesionConProveedorCommand(ProveedorIdentidad.GOOGLE, "un-code", "un-verifier",
                "https://app.renaser.dev/callback", "203.0.113.7");
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
