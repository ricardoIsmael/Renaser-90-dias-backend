package com.renaser.os.users.infrastructure.adapter.in.web.security;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.objenesis.SpringObjenesis;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.handler.MappedInterceptor;
import org.springframework.web.util.ServletRequestPathUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <b>Ningún endpoint de la API deja pasar a una cuenta SUSPENDIDA</b>, sea del rol que sea, salvo los
 * pocos que lo toleran a propósito y están nombrados abajo con su porqué (E-366, D-214).
 *
 * <p><b>El hueco que cierra (SUS-04, e2e del 2026-09-27).</b> Un mentor suspendido en la base, con el
 * token todavía vivo, seguía leyendo los hábitos y el Código Renaser de su alumno, su contexto, su
 * evaluación, la presencia del chat y el ranking: el interceptor solo miraba la suspensión de TRAINEE
 * (A-1) y cada servicio de mentor tenía que acordarse de mirarla por su cuenta. Cuatro se acordaron
 * (E-258); seis no.
 *
 * <p><b>Por qué esta prueba y no una por endpoint.</b> Recorre TODOS los handlers que Spring registraría
 * —los de hoy y los que se agreguen mañana— y le pregunta al interceptor real qué haría con una cuenta
 * suspendida de cada rol. Un endpoint nuevo queda cubierto sin que nadie se acuerde de nada; lo único que
 * lo deja afuera es declararlo {@link PublicEndpoint} (que {@code EndpointAuthorizationDeclarationTest}
 * obliga a justificar) o sumarlo, con su motivo, a {@link #TOLERAN_CUENTA_SUSPENDIDA}. Y como el
 * interceptor solo corre sobre las rutas con las que se registra, otra prueba de esta clase verifica que
 * toda ruta de un controller caiga dentro de ellas.
 */
class CuentaSuspendidaEnTodoEndpointTest {

    /**
     * Los únicos handlers que una cuenta suspendida puede seguir usando. Son los de
     * {@link Permission#OPEN_SUPPORT_TICKET}, el único permiso que {@link Permission#toleraCuentaSuspendida()}
     * deja pasar: quien está suspendido tiene que poder reclamar su suspensión.
     * {@link #laListaDeToleradosEsExactamenteLaDeLosPermisosQueToleran()} impide que esta lista y ese
     * método se desalineen en cualquiera de los dos sentidos.
     */
    private static final Map<String, String> TOLERAN_CUENTA_SUSPENDIDA = Map.of(
            "TicketSoporteController#abrir", "abrir un ticket para reclamar la suspensión",
            "TicketSoporteController#misTickets", "leer la respuesta a ese reclamo",
            "TicketSoporteController#solicitarUrlAdjunto", "adjuntarle una captura al reclamo");

    private static final List<Class<?>> CONTROLLERS = controllers();

    @Test
    @DisplayName("todo endpoint autenticado le da 403 a una cuenta SUSPENDIDA, de cualquier rol")
    @SuppressWarnings("unchecked")
    void ningunEndpointDejaPasarAUnaCuentaSuspendida() throws Exception {
        UserSummaryFinder finder = mock(UserSummaryFinder.class);
        ObjectProvider<UserSummaryFinder> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(finder);
        UserRole[] rolDelActor = new UserRole[1];
        when(finder.findById(any())).thenAnswer(invocacion -> Optional.of(new UserSummary(
                invocacion.getArgument(0), "Cuenta suspendida", null, rolDelActor[0], UserStatus.SUSPENDED)));
        // El valor de produccion: MENTOR_LEAD en modo sombra (DL-08). La suspension no depende de el.
        PermissionEnforcementInterceptor interceptor = new PermissionEnforcementInterceptor(provider, false);

        Set<String> dejanPasar = new TreeSet<>();
        int revisados = 0;
        for (HandlerMethod handler : handlersAutenticados()) {
            if (TOLERAN_CUENTA_SUSPENDIDA.containsKey(identidad(handler))) {
                continue;
            }
            revisados++;
            for (UserRole rol : UserRole.values()) {
                rolDelActor[0] = rol;
                MockHttpServletRequest request = new MockHttpServletRequest();
                request.addHeader("X-Actor-Id", UUID.randomUUID().toString());
                MockHttpServletResponse response = new MockHttpServletResponse();

                boolean continua = interceptor.preHandle(request, response, handler);

                if (continua || response.getStatus() != 403) {
                    dejanPasar.add(identidad(handler) + " (" + rol + ")");
                }
            }
        }

        assertThat(revisados)
                .as("no se encontraron los handlers: la prueba quedo ciega")
                .isGreaterThan(150);
        assertThat(dejanPasar)
                .as("""
                        Estos endpoints dejan pasar a una cuenta SUSPENDIDA con el token todavia vivo \
                        (.claude/rules/03, E-366). O el interceptor dejo de mirar la suspension, o el \
                        endpoint es de los que la toleran a proposito y falta nombrarlo, con su motivo, \
                        en TOLERAN_CUENTA_SUSPENDIDA.""")
                .isEmpty();
    }

    @Test
    @DisplayName("la lista de los que toleran la suspension es exactamente la de los permisos que la toleran")
    void laListaDeToleradosEsExactamenteLaDeLosPermisosQueToleran() {
        Set<String> toleranPorSuPermiso = new TreeSet<>();
        for (HandlerMethod handler : handlersAutenticados()) {
            Permission permiso = permisoEfectivo(handler);
            if (permiso != null && permiso.toleraCuentaSuspendida()) {
                toleranPorSuPermiso.add(identidad(handler));
            }
        }

        assertThat(toleranPorSuPermiso)
                .as("""
                        Un handler nuevo con un permiso que tolera la suspension (o un permiso que paso a \
                        tolerarla) tiene que quedar nombrado en TOLERAN_CUENTA_SUSPENDIDA con su motivo; y \
                        uno que ya no la tolera, salir de la lista.""")
                .containsExactlyInAnyOrderElementsOf(TOLERAN_CUENTA_SUSPENDIDA.keySet());
    }

    /**
     * El interceptor solo corre sobre las rutas con las que {@link PermissionEnforcementWebConfig} lo
     * registra. Un controller montado fuera de ellas (otra version de la API, una ruta sin {@code /api/v1})
     * no tendria ningun chequeo, aunque declarara su permiso perfecto.
     */
    @Test
    @DisplayName("toda ruta de un endpoint autenticado pasa por el interceptor")
    @SuppressWarnings("unchecked")
    void todaRutaAutenticadaPasaPorElInterceptor() {
        RegistroLegible registro = new RegistroLegible();
        new PermissionEnforcementWebConfig(mock(ObjectProvider.class), false).addInterceptors(registro);
        List<MappedInterceptor> delInterceptor = registro.registrados().stream()
                .filter(MappedInterceptor.class::isInstance)
                .map(MappedInterceptor.class::cast)
                .filter(m -> m.getInterceptor() instanceof PermissionEnforcementInterceptor)
                .toList();
        assertThat(delInterceptor)
                .as("PermissionEnforcementWebConfig tiene que registrar el interceptor con rutas")
                .hasSize(1);
        MappedInterceptor mapeo = delInterceptor.getFirst();

        Set<String> sinInterceptor = new TreeSet<>();
        int rutas = 0;
        for (HandlerMethod handler : handlersAutenticados()) {
            for (String ruta : rutasDe(handler)) {
                rutas++;
                MockHttpServletRequest request = new MockHttpServletRequest("GET", ruta.replaceAll("\\{[^}]+}", "x"));
                ServletRequestPathUtils.parseAndCache(request);
                if (!mapeo.matches(request)) {
                    sinInterceptor.add(ruta + " (" + identidad(handler) + ")");
                }
            }
        }

        assertThat(rutas).as("no se encontraron rutas: la prueba quedo ciega").isGreaterThan(150);
        assertThat(sinInterceptor)
                .as("rutas de endpoints autenticados a las que no llega el interceptor de permisos")
                .isEmpty();
    }

    // ─── como se arman los handlers ────────────────────────────────────────────────

    /** Lo mismo que registraria Spring MVC: clases {@code @Controller}/{@code @RestController} del codigo de produccion. */
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

    /** Todo handler HTTP que no sea {@link PublicEndpoint}, con una instancia real de su controller (sin dependencias). */
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

    /** Ruta completa = la de la clase (si tiene) + la del metodo; cualquiera de las dos puede ser varias o faltar. */
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

    /** {@code getInterceptors()} es protegido: esta subclase solo lo deja leer, para ver que registro la config. */
    private static final class RegistroLegible extends InterceptorRegistry {
        List<Object> registrados() {
            return getInterceptors();
        }
    }
}
