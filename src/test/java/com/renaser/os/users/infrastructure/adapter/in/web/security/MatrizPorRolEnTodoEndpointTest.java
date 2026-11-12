package com.renaser.os.users.infrastructure.adapter.in.web.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.renaser.os.users.application.ports.out.matriz.AnotarDenegacionDeLaMatrizPort;
import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatrizFijadoPort;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.objenesis.SpringObjenesis;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <b>Cada rol, en cada endpoint de la API: lo no aprobado responde 403 y lo aprobado no</b> (spec 050, tramo H, FR-043; misma técnica
 * que {@code CuentaSuspendidaEnTodoEndpointTest}: recorre todos los handlers que Spring registraría —los de hoy y los de mañana— y
 * le pregunta al interceptor real qué haría).
 *
 * <p><b>Las tablas de abajo se escriben a mano, no salen de {@code UserRole.can}</b>: son las que aprobó el dueño (D18 para Mentor y
 * Guía; la de MENTOR_LEAD del SDD 002; la de TRAINEE). Con la matriz del código como fuente, la prueba afirmaría lo que el código ya
 * hace y no vería un permiso que alguien agregó por error. Un endpoint nuevo queda cubierto sin que nadie se acuerde: su permiso
 * cae en la tabla del rol o es un 403.
 *
 * <p>En {@code SOMBRA} nadie recibe un 403 por la matriz (solo se anota); en {@code APLICAR} sí. ADMIN y ALCHEMIST siguen sin matriz
 * (A-1): pasan todo salvo lo exclusivo de Soporte TI. Es el chequeo estático; el E2E por rol contra el backend local (T072) mira lo
 * que esta prueba no puede: que cada pantalla de Mentoría siga funcionando.
 */
class MatrizPorRolEnTodoEndpointTest {

    /** D18 de la spec 050: los 12 de Mentor y Guía. */
    private static final Set<Permission> MENTOR_Y_GUIA = EnumSet.of(
            Permission.USE_APP, Permission.FOLLOW_OWN_PROGRAM, Permission.PUBLISH_ON_WALL, Permission.TRACK_PROGRAM_AS_STAFF,
            Permission.OPEN_SUPPORT_TICKET, Permission.USE_MENTOR_TICKETS, Permission.ANSWER_MENTOR_TICKET,
            Permission.VIEW_OWN_PHASE_CONTRACTS, Permission.SIGN_PHASE_CONTRACT, Permission.MANAGE_CALENDAR,
            Permission.MANAGE_CELLS, Permission.MANAGE_COHORTS);

    /** SDD 002 (DL-08), D-278 y spec 044: los 12 del líder de mentores. */
    private static final Set<Permission> MENTOR_LEAD = EnumSet.of(
            Permission.USE_APP, Permission.TRACK_PROGRAM_AS_STAFF, Permission.FOLLOW_OWN_PROGRAM, Permission.PUBLISH_ON_WALL,
            Permission.OPEN_SUPPORT_TICKET, Permission.VIEW_ALL_MENTOR_TICKETS, Permission.VIEW_MENTOR_CORPS,
            Permission.VIEW_MENTOR_REPORT, Permission.FOLLOW_UP_MENTOR, Permission.SET_MENTOR_OPERATIONAL_STATUS,
            Permission.EXPORT_RANKING, Permission.MANAGE_CALENDAR);

    /** Los 8 del aprendiz. */
    private static final Set<Permission> TRAINEE = EnumSet.of(
            Permission.USE_APP, Permission.FOLLOW_OWN_PROGRAM, Permission.PUBLISH_ON_WALL, Permission.OPEN_SUPPORT_TICKET,
            Permission.USE_MENTOR_TICKETS, Permission.OPEN_MENTOR_TICKET, Permission.VIEW_OWN_PHASE_CONTRACTS,
            Permission.SIGN_PHASE_CONTRACT);

    /** Los prefijos de la app de acompañamiento: lo que Mentor y Guía usan a diario, que ningún permiso puede cerrarles. */
    private static final List<String> RUTAS_DE_ACOMPANAMIENTO = List.of("/api/v1/mentor/", "/api/v1/me/");

    private static final List<Class<?>> CONTROLLERS = controllers();

    private final UserSummaryFinder finder = mock(UserSummaryFinder.class);
    private final UserRole[] rolDelActor = new UserRole[1];
    private final Logger logDelControl = (Logger) LoggerFactory.getLogger(ControlDeLaMatrizDeMentor.class);
    private Level nivelDeAntes;

    /** Esta prueba provoca cientos de denegaciones a propósito: sin esto el log de la suite se llena de líneas de «matriz-estricta». */
    @BeforeEach
    void callarLaSombra() {
        nivelDeAntes = logDelControl.getLevel();
        logDelControl.setLevel(Level.OFF);
    }

    @AfterEach
    void devolverElNivel() {
        logDelControl.setLevel(nivelDeAntes);
    }

    private PermissionEnforcementInterceptor interceptor(boolean aplicar) {
        when(finder.findById(any())).thenAnswer(invocacion -> java.util.Optional.of(new UserSummary(
                invocacion.getArgument(0), "Cuenta de prueba", null, rolDelActor[0], UserStatus.ACTIVE)));
        StaticListableBeanFactory conFinder = new StaticListableBeanFactory();
        conFinder.addBean("finder", finder);
        StaticListableBeanFactory sinNada = new StaticListableBeanFactory();
        ControlDeLaMatrizDeMentor control = new ControlDeLaMatrizDeMentor(aplicar,
                sinNada.getBeanProvider(ModoDeLaMatrizFijadoPort.class), sinNada.getBeanProvider(AnotarDenegacionDeLaMatrizPort.class),
                Duration.ofSeconds(30));
        ObjectProvider<UserSummaryFinder> provider = conFinder.getBeanProvider(UserSummaryFinder.class);
        return new PermissionEnforcementInterceptor(provider, aplicar, control);
    }

    /** Lo que debe pasar, según la tabla aprobada de cada rol (y no según el código). */
    private static boolean debePasar(UserRole rol, Permission permiso, boolean aplicar) {
        if (permiso == null) {
            return true; // sin @RequiresPermission: la matriz no decide por ellos (HANDLERS_SIN_CLASIFICAR)
        }
        if (permiso == Permission.OPERATE_PLATFORM) {
            return rol == UserRole.IT_SUPPORT;
        }
        return switch (rol) {
            case MENTOR, TRAINEE_GUIDE -> !aplicar || MENTOR_Y_GUIA.contains(permiso);
            case MENTOR_LEAD -> !aplicar || MENTOR_LEAD.contains(permiso);
            case TRAINEE -> TRAINEE.contains(permiso); // estricto siempre, sin modo sombra
            case ADMIN, ALCHEMIST, IT_SUPPORT -> true; // A-1: sin matriz
        };
    }

    private Set<String> losQueNoCoinciden(boolean aplicar, Set<UserRole> roles) throws Exception {
        PermissionEnforcementInterceptor interceptor = interceptor(aplicar);
        Set<String> distintos = new TreeSet<>();
        for (HandlerMethod handler : handlersAutenticados()) {
            Permission permiso = permisoEfectivo(handler);
            for (UserRole rol : roles) {
                rolDelActor[0] = rol;
                MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/x");
                request.addHeader("X-Actor-Id", UUID.randomUUID().toString());
                MockHttpServletResponse response = new MockHttpServletResponse();

                boolean paso = interceptor.preHandle(request, response, handler);

                boolean esperado = debePasar(rol, permiso, aplicar);
                if (paso != esperado || (!paso && response.getStatus() != 403)) {
                    distintos.add("%s %s con %s (%s): %s".formatted(rol, aplicar ? "APLICAR" : "SOMBRA", permiso, identidad(handler),
                            paso ? "pasó y debía dar 403" : "dio " + response.getStatus() + " y debía pasar"));
                }
            }
        }
        return distintos;
    }

    // spec 050 FR-043
    @Test
    @DisplayName("APLICAR: en todo endpoint, cada rol recibe 403 en lo que su tabla aprobada no incluye y pasa lo que sí incluye")
    void enAplicarCadaRolSoloPasaLoAprobado() throws Exception {
        Set<String> distintos = losQueNoCoinciden(true, EnumSet.allOf(UserRole.class));

        assertThat(distintos)
                .as("""
                        Estos pares rol/endpoint no responden lo que dice la tabla aprobada por el dueño (spec 050, D18). O la \
                        matriz de UserRole cambió sin que cambiara la tabla, o un endpoint nuevo usa un permiso que el rol no \
                        tiene: si es legítimo, hay que pedirle al dueño que lo apruebe (regla 00); si no, el endpoint pide otro \
                        permiso.""")
                .isEmpty();
    }

    // spec 050 FR-043
    @Test
    @DisplayName("SOMBRA: en ningún endpoint la matriz de Mentor y Guía responde 403 (solo anota), y el aprendiz sigue estricto")
    void enSombraNadieDeMentoriaRecibe403() throws Exception {
        Set<String> distintos = losQueNoCoinciden(false, EnumSet.allOf(UserRole.class));

        assertThat(distintos).as("en sombra Mentor, Guía y líder pasan todo; el aprendiz y Soporte TI son estrictos").isEmpty();
    }

    // spec 050 FR-043
    @Test
    @DisplayName("ningún permiso cierra las rutas de acompañamiento (/mentor/** y /me/**) a Mentor y Guía en APLICAR")
    void lasRutasDeAcompanamientoQuedanAbiertas() throws Exception {
        PermissionEnforcementInterceptor interceptor = interceptor(true);
        Set<String> cerradas = new TreeSet<>();
        int revisadas = 0;
        for (HandlerMethod handler : handlersAutenticados()) {
            if (RUTAS_DE_ACOMPANAMIENTO.stream().noneMatch(prefijo -> rutasDe(handler).stream().anyMatch(r -> r.startsWith(prefijo)))) {
                continue;
            }
            revisadas++;
            for (UserRole rol : UserRole.ROLES_QUE_ACTUAN_COMO_MENTOR) {
                rolDelActor[0] = rol;
                MockHttpServletRequest request = new MockHttpServletRequest();
                request.addHeader("X-Actor-Id", UUID.randomUUID().toString());
                if (!interceptor.preHandle(request, new MockHttpServletResponse(), handler)) {
                    cerradas.add(identidad(handler) + " (" + rol + ", " + permisoEfectivo(handler) + ")");
                }
            }
        }

        assertThat(revisadas).as("no se encontraron las rutas de acompañamiento: la prueba quedó ciega").isGreaterThan(20);
        assertThat(cerradas)
                .as("Un permiso de la matriz de mentores le cerraría a Mentor y Guía una pantalla de acompañamiento (spec 050, historia 8)")
                .isEmpty();
    }

    // ─── como se arman los handlers (los mismos de CuentaSuspendidaEnTodoEndpointTest) ───────────────

    private static List<Class<?>> controllers() {
        List<Class<?>> encontrados = new ArrayList<>();
        for (JavaClass clase : new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.renaser.os")) {
            boolean esController = clase.isAnnotatedWith(Controller.class) || clase.isMetaAnnotatedWith(Controller.class);
            if (esController && !clase.isInterface()
                    && !clase.getModifiers().contains(com.tngtech.archunit.core.domain.JavaModifier.ABSTRACT)) {
                encontrados.add(clase.reflect());
            }
        }
        return encontrados;
    }

    private static List<HandlerMethod> handlersAutenticados() {
        SpringObjenesis objenesis = new SpringObjenesis();
        List<HandlerMethod> handlers = new ArrayList<>();
        for (Class<?> controller : CONTROLLERS) {
            Object instancia = objenesis.newInstance(controller);
            for (Method metodo : controller.getMethods()) {
                if (esHandler(metodo) && !esPublico(controller, metodo)) {
                    handlers.add(new HandlerMethod(instancia, metodo));
                }
            }
        }
        return handlers;
    }

    private static boolean esHandler(Method metodo) {
        return !metodo.isBridge() && !metodo.isSynthetic() && !Modifier.isStatic(metodo.getModifiers())
                && AnnotatedElementUtils.hasAnnotation(metodo, RequestMapping.class);
    }

    private static boolean esPublico(Class<?> controller, Method metodo) {
        return metodo.isAnnotationPresent(PublicEndpoint.class) || controller.isAnnotationPresent(PublicEndpoint.class);
    }

    private static Permission permisoEfectivo(HandlerMethod handler) {
        RequiresPermission delMetodo = handler.getMethodAnnotation(RequiresPermission.class);
        if (delMetodo != null) {
            return delMetodo.value();
        }
        RequiresPermission deLaClase = handler.getBeanType().getAnnotation(RequiresPermission.class);
        return deLaClase == null ? null : deLaClase.value();
    }

    private static List<String> rutasDe(HandlerMethod handler) {
        RequestMapping deLaClase = AnnotatedElementUtils.findMergedAnnotation(handler.getBeanType(), RequestMapping.class);
        RequestMapping delMetodo = AnnotatedElementUtils.findMergedAnnotation(handler.getMethod(), RequestMapping.class);
        String[] bases = deLaClase == null || deLaClase.path().length == 0 ? new String[] {""} : deLaClase.path();
        String[] sufijos = delMetodo == null || delMetodo.path().length == 0 ? new String[] {""} : delMetodo.path();
        List<String> rutas = new ArrayList<>();
        for (String base : bases) {
            for (String sufijo : sufijos) {
                String ruta = (base + "/" + sufijo).replaceAll("//+", "/");
                rutas.add(ruta.length() > 1 && ruta.endsWith("/") ? ruta.substring(0, ruta.length() - 1) : ruta);
            }
        }
        return rutas;
    }

    private static String identidad(HandlerMethod handler) {
        return handler.getBeanType().getSimpleName() + "#" + handler.getMethod().getName();
    }
}
