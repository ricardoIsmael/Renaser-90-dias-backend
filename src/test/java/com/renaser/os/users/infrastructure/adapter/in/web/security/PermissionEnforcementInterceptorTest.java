package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Cubre A-1: el interceptor que hace CUMPLIR {@code @RequiresPermission}, no solo declararlo.
 * CLAUDE.MD §0.3: toda prueba de autorizacion negativa vive aca — un TRAINEE sin el permiso
 * tiene que recibir 403, un SUSPENDED tiene que recibir 403 aunque el permiso lo tendria, y un
 * rol sin matriz definida (hueco temporal A-1) tiene que seguir pasando como pasaba antes.
 *
 * <p>Desde el SDD 002 (DL-08) cubre ademas MENTOR_LEAD, que si tiene matriz pero arranca en
 * <b>modo sombra</b>: con el interruptor apagado se evalua y se deja pasar; con el encendido,
 * deniega. Las dos ramas se prueban aca, para que activar el paso 3 no sea un salto a ciegas.
 */
@SuppressWarnings("unchecked")
class PermissionEnforcementInterceptorTest {

    private final UserSummaryFinder userSummaryFinder = mock(UserSummaryFinder.class);
    private final ObjectProvider<UserSummaryFinder> provider = mock(ObjectProvider.class);
    /** El de produccion hoy: MENTOR_LEAD en modo sombra (SDD 002, DL-08, paso 2). */
    private final PermissionEnforcementInterceptor interceptor =
            new PermissionEnforcementInterceptor(provider, false);

    /** El mismo con el paso 3 encendido, para probar el cumplimiento antes de activarlo. */
    private final PermissionEnforcementInterceptor interceptorConCumplimiento =
            new PermissionEnforcementInterceptor(provider, true);

    @BeforeEach
    void configurarProviderDisponible() {
        when(provider.getIfAvailable()).thenReturn(userSummaryFinder);
    }

    @AfterEach
    void limpiarContexto() {
        SecurityContextHolder.clearContext();
    }

    // ─── el caso central: TRAINEE sin el permiso -> 403 ────────────────────────────────

    @Test
    @DisplayName("TRAINEE contra un endpoint que exige MODERATE_WALL recibe 403")
    void traineeSinElPermisoRecibe403() throws Exception {
        UUID actorId = actorTrainee(UserStatus.ACTIVE);

        boolean continua = ejecutarPreHandle(actorId, "exigeModerateWall");

        assertThat(continua).isFalse();
        assertThat(ultimaRespuesta.getStatus()).isEqualTo(403);
        assertThat(ultimaRespuesta.getContentAsString())
                .as("el mensaje no debe revelar el permiso ni el rol del actor")
                .doesNotContain("MODERATE_WALL", "TRAINEE", "role");
    }

    @Test
    @DisplayName("TRAINEE contra un endpoint que exige USE_APP (uno de los 8) pasa")
    void traineeConUsoAppPasa() throws Exception {
        UUID actorId = actorTrainee(UserStatus.ACTIVE);

        boolean continua = ejecutarPreHandle(actorId, "exigeUseApp");

        assertThat(continua).isTrue();
        assertThat(ultimaRespuesta.getStatus()).isEqualTo(200); // default: nadie lo toco
    }

    // ─── actor SUSPENDED: corta antes que el permiso ───────────────────────────────────

    @Test
    @DisplayName("un actor SUSPENDED recibe 403 aunque el permiso se lo daria")
    void actorSuspendidoRecibe403AunqueTengaElPermiso() throws Exception {
        UUID actorId = actorTrainee(UserStatus.SUSPENDED);

        boolean continua = ejecutarPreHandle(actorId, "exigeUseApp");

        assertThat(continua).isFalse();
        assertThat(ultimaRespuesta.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("OPEN_SUPPORT_TICKET tolera un actor SUSPENDED (tiene que poder reclamar su suspension)")
    void openSupportTicketPasaAunqueElActorEsteSuspendido() throws Exception {
        UUID actorId = actorTrainee(UserStatus.SUSPENDED);

        boolean continua = ejecutarPreHandle(actorId, "exigeOpenSupportTicket");

        assertThat(continua).isTrue();
    }

    // ─── @PublicEndpoint: nunca se verifica ────────────────────────────────────────────

    @Test
    @DisplayName("@PublicEndpoint pasa sin sesion y sin header, y ni siquiera consulta al actor")
    void publicEndpointPasaSinConsultarNada() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean continua = interceptor.preHandle(request, response, handlerMethodFor("publico"));

        assertThat(continua).isTrue();
        verifyNoInteractions(userSummaryFinder);
    }

    // ─── handler sin ninguna de las dos anotaciones (deuda declarada de fase 4) ────────

    @Test
    @DisplayName("un handler sin @RequiresPermission ni @PublicEndpoint pasa (no le corresponde a este interceptor decidir)")
    void handlerSinAnotarPasa() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean continua = interceptor.preHandle(request, response, handlerMethodFor("sinAnotar"));

        assertThat(continua).isTrue();
    }

    // ─── actor no resoluble: se deja que el binding normal lo resuelva como siempre ───

    @Test
    @DisplayName("sin sesion y sin header X-Actor-Id, un endpoint protegido pasa igual (lo resuelve el binding del controller)")
    void sinActorResolublePasaYNoConsultaAlFinder() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean continua = interceptor.preHandle(request, response, handlerMethodFor("exigeModerateWall"));

        assertThat(continua).isTrue();
        verifyNoInteractions(userSummaryFinder);
    }

    @Test
    @DisplayName("un actorId que no existe pasa (lo resuelve el guard/caso de uso, tipicamente 404)")
    void actorInexistentePasa() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(Optional.empty());

        boolean continua = ejecutarPreHandle(actorId, "exigeModerateWall");

        assertThat(continua).isTrue();
    }

    // ─── el hueco temporal y deliberado: roles sin matriz pasan cualquier cosa ────────

    @Test
    @DisplayName("TEMPORAL: un ADMIN contra un endpoint de administracion pasa (matriz de ADMIN no definida todavia, A-1)")
    void adminPasaPorqueSuMatrizNoEstaDefinidaTodavia() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(
                Optional.of(new UserSummary(UserId.of(actorId), "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));

        boolean continua = ejecutarPreHandle(actorId, "exigeManageStaff");

        assertThat(continua).isTrue();
    }

    @Test
    @DisplayName("TEMPORAL: un ADMIN suspendido tambien pasa (la verificacion real es solo para TRAINEE, A-1)")
    void adminSuspendidoTambienPasaPorqueNoEsElRolVerificado() throws Exception {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(
                Optional.of(new UserSummary(UserId.of(actorId), "Admin", null, UserRole.ADMIN, UserStatus.SUSPENDED)));

        boolean continua = ejecutarPreHandle(actorId, "exigeManageStaff");

        assertThat(continua).isTrue();
    }

    // ─── MENTOR_LEAD: matriz real, modo sombra y cumplimiento (SDD 002, DL-08) ───────

    @Test
    @DisplayName("MENTOR_LEAD contra un endpoint de administracion: en modo sombra pasa, pero ya se evaluo")
    void mentorLeadEnModoSombraPasaAunqueNoTengaElPermiso() throws Exception {
        UUID actorId = actorConRol(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        boolean continua = ejecutarPreHandle(actorId, "exigeManageStaff");

        assertThat(continua).isTrue();
        assertThat(ultimaRespuesta.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("MENTOR_LEAD contra un endpoint de administracion: con el cumplimiento encendido recibe 403")
    void mentorLeadConCumplimientoRecibe403EnAdministracion() throws Exception {
        UUID actorId = actorConRol(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        boolean continua = ejecutarPreHandleCon(interceptorConCumplimiento, actorId, "exigeManageStaff");

        assertThat(continua).isFalse();
        assertThat(ultimaRespuesta.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("MENTOR_LEAD SI puede ver el padron de mentores, con el cumplimiento encendido")
    void mentorLeadPuedeVerElPadronDeMentores() throws Exception {
        UUID actorId = actorConRol(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        boolean continua = ejecutarPreHandleCon(interceptorConCumplimiento, actorId, "exigeVerPadronDeMentores");

        assertThat(continua).isTrue();
    }

    @Test
    @DisplayName("MENTOR_LEAD conserva USE_APP: cerrar el falla-abierto no le rompe su propia app")
    void mentorLeadConservaUseApp() throws Exception {
        UUID actorId = actorConRol(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        boolean continua = ejecutarPreHandleCon(interceptorConCumplimiento, actorId, "exigeUseApp");

        assertThat(continua).isTrue();
    }

    @Test
    @DisplayName("un MENTOR_LEAD suspendido recibe 403 con el cumplimiento encendido, aunque el permiso lo tendria")
    void mentorLeadSuspendidoRecibe403() throws Exception {
        UUID actorId = actorConRol(UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);

        boolean continua = ejecutarPreHandleCon(interceptorConCumplimiento, actorId, "exigeUseApp");

        assertThat(continua).isFalse();
        assertThat(ultimaRespuesta.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("un MENTOR_LEAD suspendido sigue pudiendo abrir un ticket de soporte (reclamar su suspension)")
    void mentorLeadSuspendidoPuedeAbrirTicketDeSoporte() throws Exception {
        UUID actorId = actorConRol(UserRole.MENTOR_LEAD, UserStatus.SUSPENDED);

        boolean continua = ejecutarPreHandleCon(interceptorConCumplimiento, actorId, "exigeOpenSupportTicket");

        assertThat(continua).isTrue();
    }

    @Test
    @DisplayName("un MENTOR no llega al padron de mentores: no es su rol, aunque su matriz siga sin definirse")
    void unMentorNoEsUnLiderDeMentores() throws Exception {
        UUID actorId = actorConRol(UserRole.MENTOR, UserStatus.ACTIVE);

        // MENTOR sigue en falla-abierto (A-1), asi que el interceptor lo deja pasar: quien lo
        // frena es el guard del servicio. Este test fija ese limite a proposito, para que se vea
        // que la matriz de MENTOR sigue siendo deuda y no quede la ilusion de que esta cerrada.
        boolean continua = ejecutarPreHandleCon(interceptorConCumplimiento, actorId, "exigeVerPadronDeMentores");

        assertThat(continua).isTrue();
    }

    @Test
    @DisplayName("un TRAINEE no llega al padron de mentores: 403 del interceptor")
    void unTraineeNoLlegaAlPadronDeMentores() throws Exception {
        UUID actorId = actorTrainee(UserStatus.ACTIVE);

        boolean continua = ejecutarPreHandle(actorId, "exigeVerPadronDeMentores");

        assertThat(continua).isFalse();
        assertThat(ultimaRespuesta.getStatus()).isEqualTo(403);
    }

    // ─── resiliencia frente a un @WebMvcTest de otro modulo sin UserSummaryFinder ─────

    @Test
    @DisplayName("si UserSummaryFinder no esta disponible en el contexto (otro @WebMvcTest), el interceptor deja pasar")
    void sinUserSummaryFinderDisponiblePasa() throws Exception {
        when(provider.getIfAvailable()).thenReturn(null);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Actor-Id", UUID.randomUUID().toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean continua = interceptor.preHandle(request, response, handlerMethodFor("exigeModerateWall"));

        assertThat(continua).isTrue();
    }

    @Test
    @DisplayName("resuelve el actor desde la sesion cuando hay una, sin mirar el header")
    void resuelveDesdeLaSesionSiHay() throws Exception {
        UUID actorId = UUID.randomUUID();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(actorId.toString(), null, List.of()));
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(
                Optional.of(new UserSummary(UserId.of(actorId), "T", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        MockHttpServletRequest request = new MockHttpServletRequest(); // sin header
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean continua = interceptor.preHandle(request, response, handlerMethodFor("exigeModerateWall"));

        assertThat(continua).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("un token anonimo no cuenta como sesion: cae al header igual que ActorAutenticadoArgumentResolver")
    void tokenAnonimoCaeAlHeader() throws Exception {
        UUID actorId = actorTrainee(UserStatus.ACTIVE);
        SecurityContextHolder.getContext().setAuthentication(
                new AnonymousAuthenticationToken("clave", "anonymousUser",
                        List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Actor-Id", actorId.toString());
        MockHttpServletResponse response = new MockHttpServletResponse();

        boolean continua = interceptor.preHandle(request, response, handlerMethodFor("exigeModerateWall"));

        assertThat(continua).isFalse(); // TRAINEE no tiene MODERATE_WALL: prueba que SI resolvio el actor via header
    }

    // ─── helpers ────────────────────────────────────────────────────────────────────

    private MockHttpServletResponse ultimaRespuesta;

    private UUID actorTrainee(UserStatus status) {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(
                Optional.of(new UserSummary(UserId.of(actorId), "T", null, UserRole.TRAINEE, status)));
        return actorId;
    }

    private UUID actorConRol(UserRole rol, UserStatus status) {
        UUID actorId = UUID.randomUUID();
        when(userSummaryFinder.findById(UserId.of(actorId))).thenReturn(
                Optional.of(new UserSummary(UserId.of(actorId), "X", null, rol, status)));
        return actorId;
    }

    private boolean ejecutarPreHandle(UUID actorId, String metodo) throws Exception {
        return ejecutarPreHandleCon(interceptor, actorId, metodo);
    }

    private boolean ejecutarPreHandleCon(PermissionEnforcementInterceptor cual, UUID actorId, String metodo)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Actor-Id", actorId.toString());
        ultimaRespuesta = new MockHttpServletResponse();
        return cual.preHandle(request, ultimaRespuesta, handlerMethodFor(metodo));
    }

    private static HandlerMethod handlerMethodFor(String nombreMetodo) throws NoSuchMethodException {
        Method metodo = ControladorDePrueba.class.getDeclaredMethod(nombreMetodo);
        return new HandlerMethod(new ControladorDePrueba(), metodo);
    }

    /** Controller ficticio, solo para tener metodos anotados reales que reflejar. */
    static class ControladorDePrueba {

        @RequiresPermission(Permission.MODERATE_WALL)
        public void exigeModerateWall() {
        }

        @RequiresPermission(Permission.USE_APP)
        public void exigeUseApp() {
        }

        @RequiresPermission(Permission.OPEN_SUPPORT_TICKET)
        public void exigeOpenSupportTicket() {
        }

        @RequiresPermission(Permission.MANAGE_STAFF)
        public void exigeManageStaff() {
        }

        @RequiresPermission(Permission.VIEW_MENTOR_CORPS)
        public void exigeVerPadronDeMentores() {
        }

        @PublicEndpoint("prueba")
        public void publico() {
        }

        public void sinAnotar() {
        }
    }
}
