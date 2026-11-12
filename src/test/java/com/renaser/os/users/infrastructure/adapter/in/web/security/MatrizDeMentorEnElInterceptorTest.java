package com.renaser.os.users.infrastructure.adapter.in.web.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.out.matriz.AnotarDenegacionDeLaMatrizPort;
import com.renaser.os.users.application.ports.out.matriz.DenegacionDeLaMatriz;
import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatriz;
import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatrizFijadoPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * La matriz estricta de MENTOR y TRAINEE_GUIDE en el interceptor (spec 050, tramo H, FR-041), en los dos modos y rol por rol:
 * en {@code SOMBRA} lo que la lista no permite pasa pero se anota (sin la cuenta), en {@code APLICAR} responde el 403 de siempre, y
 * lo que la lista permite pasa en los dos sin dejar rastro. El resto de los roles no se entera de este interruptor.
 */
class MatrizDeMentorEnElInterceptorTest {

    private static final String RUTA = "/api/v1/prueba/{id}";

    private final UserSummaryFinder finder = mock(UserSummaryFinder.class);
    private final AnotarDenegacionDeLaMatrizPort anotador = mock(AnotarDenegacionDeLaMatrizPort.class);
    private final ModoDeLaMatrizFijadoPort modoFijado = mock(ModoDeLaMatrizFijadoPort.class);
    private final ListAppender<ILoggingEvent> lineasDelLog = new ListAppender<>();
    private final Logger logDelControl = (Logger) LoggerFactory.getLogger(ControlDeLaMatrizDeMentor.class);

    private MockHttpServletResponse respuesta;

    @BeforeEach
    void escucharElLog() {
        lineasDelLog.start();
        logDelControl.addAppender(lineasDelLog);
        logDelControl.setLevel(Level.WARN);
        when(modoFijado.fijado()).thenReturn(Optional.empty());
    }

    @AfterEach
    void dejarElLog() {
        logDelControl.detachAppender(lineasDelLog);
    }

    /** El interceptor con MENTOR y TRAINEE_GUIDE en el modo que se pida por configuración, y MENTOR_LEAD en sombra. */
    private PermissionEnforcementInterceptor interceptor(boolean aplicarMentor) {
        return interceptor(aplicarMentor, false);
    }

    private PermissionEnforcementInterceptor interceptor(boolean aplicarMentor, boolean aplicarLider) {
        ControlDeLaMatrizDeMentor control = new ControlDeLaMatrizDeMentor(aplicarMentor, proveedor(ModoDeLaMatrizFijadoPort.class, modoFijado),
                proveedor(AnotarDenegacionDeLaMatrizPort.class, anotador), Duration.ofSeconds(30));
        return new PermissionEnforcementInterceptor(proveedor(UserSummaryFinder.class, finder), aplicarLider, control);
    }

    private static <T> ObjectProvider<T> proveedor(Class<T> tipo, T bean) {
        StaticListableBeanFactory fabrica = new StaticListableBeanFactory();
        fabrica.addBean("el-unico", bean);
        return fabrica.getBeanProvider(tipo);
    }

    private UUID actor(UserRole rol, UserStatus estado) {
        UUID id = UUID.randomUUID();
        when(finder.findById(UserId.of(id))).thenReturn(Optional.of(new UserSummary(UserId.of(id), "X", null, rol, estado)));
        return id;
    }

    private boolean pedir(PermissionEnforcementInterceptor interceptor, UUID actor, String metodo) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/prueba/" + UUID.randomUUID());
        request.addHeader("X-Actor-Id", actor.toString());
        request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, RUTA);
        respuesta = new MockHttpServletResponse();
        Method handler = Controlador.class.getDeclaredMethod(metodo);
        return interceptor.preHandle(request, respuesta, new HandlerMethod(new Controlador(), handler));
    }

    // spec 050 FR-041
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("SOMBRA: lo que la lista no permite pasa, queda anotado con rol, permiso y endpoint, y el 403 no se da")
    void enSombraPasaYSeAnota(UserRole rol) throws Exception {
        UUID actor = actor(rol, UserStatus.ACTIVE);

        boolean continua = pedir(interceptor(false), actor, "exigeModerarElMuro");

        assertThat(continua).isTrue();
        assertThat(respuesta.getStatus()).isEqualTo(200);
        var capturada = forClass(DenegacionDeLaMatriz.class);
        verify(anotador).anotar(capturada.capture());
        assertThat(capturada.getValue()).isEqualTo(
                new DenegacionDeLaMatriz(ModoDeLaMatriz.SOMBRA, rol, Permission.MODERATE_WALL, "GET", RUTA));
    }

    // spec 050 FR-041
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("APLICAR: lo que la lista no permite responde 403 SIN_PERMISO sin nombrar el permiso ni el rol, y también se anota")
    void enAplicarResponde403(UserRole rol) throws Exception {
        UUID actor = actor(rol, UserStatus.ACTIVE);

        boolean continua = pedir(interceptor(true), actor, "exigeModerarElMuro");

        assertThat(continua).isFalse();
        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(respuesta.getContentAsString()).contains("\"codigo\":\"SIN_PERMISO\"")
                .doesNotContain("MODERATE_WALL", "MENTOR", "TRAINEE_GUIDE", actor.toString());
        var capturada = forClass(DenegacionDeLaMatriz.class);
        verify(anotador).anotar(capturada.capture());
        assertThat(capturada.getValue().estado()).isEqualTo("DENEGADO");
    }

    // spec 050 FR-041
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("lo que la lista permite pasa en los dos modos y no deja rastro")
    void loPermitidoPasaEnLosDosModos(UserRole rol) throws Exception {
        for (boolean aplicar : new boolean[] {false, true}) {
            UUID actor = actor(rol, UserStatus.ACTIVE);
            for (String permitido : new String[] {"exigeUsarLaApp", "exigeAdministrarGrupos", "exigeResponderTickets"}) {
                assertThat(pedir(interceptor(aplicar), actor, permitido)).as("%s %s aplicar=%s", rol, permitido, aplicar).isTrue();
                assertThat(respuesta.getStatus()).isEqualTo(200);
            }
        }
        verifyNoInteractions(anotador);
        assertThat(lineasDelLog.list).isEmpty();
    }

    // spec 050 FR-041
    @Test
    @DisplayName("la línea del log lleva estado, rol, permiso, método, plantilla de la ruta y handler, y no la cuenta ni la ruta concreta")
    void laLineaDelLogNoLlevaLaCuenta() throws Exception {
        UUID actor = actor(UserRole.MENTOR, UserStatus.ACTIVE);

        pedir(interceptor(false), actor, "exigeModerarElMuro");

        assertThat(lineasDelLog.list).singleElement().satisfies(linea -> {
            assertThat(linea.getLevel()).isEqualTo(Level.WARN);
            assertThat(linea.getFormattedMessage())
                    .contains("matriz-estricta HABRIA_DENEGADO", "rol=MENTOR", "permiso=MODERATE_WALL", "metodo=GET",
                            "ruta=" + RUTA, "handler=Controlador#exigeModerarElMuro")
                    .doesNotContain(actor.toString(), "X-Actor-Id", "token");
        });
    }

    // spec 050 FR-041
    @Test
    @DisplayName("lo que el dueño fija en Redis manda sobre la configuración, sin reiniciar: APLICAR pone el 403 y SOMBRA lo quita")
    void elModoDeRedisManda() throws Exception {
        UUID actor = actor(UserRole.MENTOR, UserStatus.ACTIVE);

        when(modoFijado.fijado()).thenReturn(Optional.of(ModoDeLaMatriz.APLICAR));
        assertThat(pedir(interceptor(false), actor, "exigeModerarElMuro")).as("sombra por configuración, APLICAR por Redis").isFalse();
        assertThat(respuesta.getStatus()).isEqualTo(403);

        when(modoFijado.fijado()).thenReturn(Optional.of(ModoDeLaMatriz.SOMBRA));
        assertThat(pedir(interceptor(true), actor, "exigeModerarElMuro")).as("aplicar por configuración, SOMBRA por Redis").isTrue();
    }

    // spec 050 FR-041
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("si anotar en Redis falla el pedido sigue igual: pasa en sombra y se niega en aplicar")
    void anotarEsAuxiliar(UserRole rol) throws Exception {
        doThrow(new IllegalStateException("Redis no responde")).when(anotador).anotar(any());
        UUID actor = actor(rol, UserStatus.ACTIVE);

        assertThatCode(() -> assertThat(pedir(interceptor(false), actor, "exigeModerarElMuro")).isTrue()).doesNotThrowAnyException();
        assertThat(pedir(interceptor(true), actor, "exigeModerarElMuro")).isFalse();
        assertThat(respuesta.getStatus()).isEqualTo(403);
    }

    // spec 050 FR-044
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("la cuenta suspendida sigue cortando antes de la matriz, en los dos modos, salvo reclamar la suspensión")
    void laSuspensionSigueIgual(UserRole rol) throws Exception {
        for (boolean aplicar : new boolean[] {false, true}) {
            UUID suspendido = actor(rol, UserStatus.SUSPENDED);

            assertThat(pedir(interceptor(aplicar), suspendido, "exigeUsarLaApp")).isFalse();
            assertThat(respuesta.getContentAsString()).contains("\"codigo\":\"CUENTA_SUSPENDIDA\"");
            assertThat(pedir(interceptor(aplicar), suspendido, "exigeAbrirUnTicketDeSoporte")).isTrue();
        }
        verifyNoInteractions(anotador);
    }

    // spec 050 FR-041
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"ADMIN", "ALCHEMIST", "IT_SUPPORT"})
    @DisplayName("ADMIN, ALCHEMIST e IT_SUPPORT no se enteran del interruptor: pasan lo que pasaban, en los dos modos")
    void losDemasRolesDeStaffNoCambian(UserRole rol) throws Exception {
        for (boolean aplicar : new boolean[] {false, true}) {
            UUID actor = actor(rol, UserStatus.ACTIVE);

            assertThat(pedir(interceptor(aplicar), actor, "exigeModerarElMuro")).as("%s aplicar=%s", rol, aplicar).isTrue();
        }
        verifyNoInteractions(anotador);
        assertThat(lineasDelLog.list).isEmpty();
    }

    // spec 050 FR-041
    @Test
    @DisplayName("el interruptor es solo de MENTOR y TRAINEE_GUIDE: el aprendiz sigue estricto, y el líder sigue con el suyo")
    void elAprendizYElLiderTienenSusPropiosInterruptores() throws Exception {
        UUID aprendiz = actor(UserRole.TRAINEE, UserStatus.ACTIVE);
        UUID lider = actor(UserRole.MENTOR_LEAD, UserStatus.ACTIVE);

        assertThat(pedir(interceptor(false), aprendiz, "exigeModerarElMuro")).as("TRAINEE siempre estricto").isFalse();
        assertThat(respuesta.getStatus()).isEqualTo(403);
        assertThat(pedir(interceptor(true, false), lider, "exigeModerarElMuro"))
                .as("MENTOR_LEAD en sombra aunque MENTOR esté en APLICAR").isTrue();
        assertThat(pedir(interceptor(false, true), lider, "exigeModerarElMuro"))
                .as("MENTOR_LEAD en APLICAR con el suyo, aunque MENTOR esté en sombra").isFalse();
        verifyNoInteractions(anotador);
    }

    // spec 050 FR-044
    @ParameterizedTest
    @EnumSource(value = UserRole.class, names = {"MENTOR", "TRAINEE_GUIDE"})
    @DisplayName("EXPORT_RANKING y OPERATE_PLATFORM siguen negados a Mentor y Guía: el segundo por la regla de Soporte TI, sin pasar por la sombra")
    void lasExcepcionesDeAntesSiguenIgual(UserRole rol) throws Exception {
        UUID actor = actor(rol, UserStatus.ACTIVE);

        assertThat(pedir(interceptor(true), actor, "exigeExportarElRanking")).isFalse();
        assertThat(pedir(interceptor(false), actor, "exigeOperarLaPlataforma")).as("sin modo sombra (D-267)").isFalse();
        assertThat(respuesta.getStatus()).isEqualTo(403);
    }

    // spec 050 FR-041
    @Test
    @DisplayName("un handler sin @RequiresPermission no pasa por la matriz de mentores (la suspensión sí lo alcanza)")
    void sinPermisoDeclaradoNoDecideLaMatriz() throws Exception {
        UUID actor = actor(UserRole.MENTOR, UserStatus.ACTIVE);

        assertThat(pedir(interceptor(true), actor, "sinAnotar")).isTrue();
        verifyNoInteractions(anotador);
        verify(anotador, never()).anotar(any());
    }

    static class Controlador {

        @RequiresPermission(Permission.MODERATE_WALL)
        public void exigeModerarElMuro() {
        }

        @RequiresPermission(Permission.USE_APP)
        public void exigeUsarLaApp() {
        }

        @RequiresPermission(Permission.MANAGE_CELLS)
        public void exigeAdministrarGrupos() {
        }

        @RequiresPermission(Permission.ANSWER_MENTOR_TICKET)
        public void exigeResponderTickets() {
        }

        @RequiresPermission(Permission.OPEN_SUPPORT_TICKET)
        public void exigeAbrirUnTicketDeSoporte() {
        }

        @RequiresPermission(Permission.EXPORT_RANKING)
        public void exigeExportarElRanking() {
        }

        @RequiresPermission(Permission.OPERATE_PLATFORM)
        public void exigeOperarLaPlataforma() {
        }

        public void sinAnotar() {
        }
    }
}
