package com.renaser.os;

import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.RequiresPermission;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El test de reflexion que exige CLAUDE.MD §0.3: <b>ningun endpoint puede quedar sin decir
 * que autorizacion exige</b>. Un handler sin {@link RequiresPermission} ni
 * {@link PublicEndpoint} rompe el build.
 *
 * <p>Las anotaciones todavia no ejecutan nada — la autorizacion real vive en los guards de
 * los servicios y {@code SecurityConfig} sigue en {@code permitAll()} (fase 4,
 * {@code docs/MODULO_AUTH.md} §9). Lo que este test protege es el <b>inventario</b>: que la
 * lista de "que endpoint necesita que" este completa y no se degrade cuando alguien agregue
 * un endpoint nuevo el mes que viene.
 *
 * <p>{@link #MODULOS_SIN_ANOTAR} es la deuda declarada, no una escapatoria: arranco con los
 * 14 modulos y se vacia de a uno. {@link #noHayExclusionesObsoletas()} impide que un modulo
 * ya anotado se quede escondido ahi adentro.
 */
class EndpointAuthorizationDeclarationTest {

    /**
     * Modulos cuyos endpoints todavia no declararon su autorizacion. Arranco con los 14 y se
     * vacio de a uno; <b>hoy esta vacia: los 14 modulos estan declarados</b>. Se deja el
     * mecanismo, no la deuda — si mañana entra un modulo nuevo entero, esta es la valvula
     * para que el build siga verde mientras se lo anota, y
     * {@link #noHayExclusionesObsoletas()} obliga a vaciarla de nuevo.
     */
    private static final Set<String> MODULOS_SIN_ANOTAR = Set.of();

    /**
     * Endpoints sueltos que no se pudieron clasificar leyendo el codigo. Cada uno lleva un
     * TODO en su controller. <b>Nunca se resuelven marcandolos publicos</b>: un publico por
     * las dudas es un agujero permanente (ver {@link PublicEndpoint}).
     */
    private static final Set<String> HANDLERS_SIN_CLASIFICAR = Set.of(
            // community: el handler no ejecuta ningun guard y el codigo no dice si eso es
            // deliberado. Detalle de cada uno en el TODO del controller.
            "TestimonioController#listar",
            "TestimonioController#crear",
            "WallCommentController#listar",
            "WallController#mine",
            "WallController#latestAuthor",
            // habits: MisHabitosService.consultar(actor) filtra por el actor pero NO ejecuta
            // ningun guard, asi que una cuenta suspendida sigue leyendo su catalogo. Anotarlo
            // con un permiso afirmaria que algo lo hace cumplir, y no es cierto.
            "MisHabitosController#listar");

    private static final List<Class<? extends Annotation>> ANOTACIONES_DE_RUTA = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class,
            PutMapping.class, PatchMapping.class, DeleteMapping.class);

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.renaser.os");

    @Test
    @DisplayName("todo endpoint declara @RequiresPermission o @PublicEndpoint")
    void everyEndpointDeclaresItsAuthorization() {
        Set<String> sinDeclarar = new TreeSet<>();
        for (JavaClass controller : controllers()) {
            if (MODULOS_SIN_ANOTAR.contains(modulo(controller))) {
                continue;
            }
            for (JavaMethod handler : handlersDe(controller)) {
                if (!declaraAutorizacion(handler) && !HANDLERS_SIN_CLASIFICAR.contains(identidad(handler))) {
                    sinDeclarar.add(identidad(handler));
                }
            }
        }
        assertThat(sinDeclarar)
                .as("endpoints sin declarar que autorizacion exigen (CLAUDE.MD §0.3). "
                        + "Si no se puede clasificar desde el codigo, va un TODO en el controller "
                        + "y el handler entra en HANDLERS_SIN_CLASIFICAR — nunca @PublicEndpoint por defecto")
                .isEmpty();
    }

    @Test
    @DisplayName("ningun endpoint es publico y protegido a la vez")
    void noEndpointIsBothPublicAndProtected() {
        Set<String> ambiguos = new TreeSet<>();
        for (JavaClass controller : controllers()) {
            for (JavaMethod handler : handlersDe(controller)) {
                if (handler.isAnnotatedWith(RequiresPermission.class)
                        && handler.isAnnotatedWith(PublicEndpoint.class)) {
                    ambiguos.add(identidad(handler));
                }
            }
        }
        assertThat(ambiguos)
                .as("un endpoint no puede exigir un permiso y ser publico al mismo tiempo")
                .isEmpty();
    }

    @Test
    @DisplayName("la lista de modulos sin anotar no esconde modulos ya terminados")
    void noHayExclusionesObsoletas() {
        Set<String> yaTerminados = new TreeSet<>();
        for (String modulo : MODULOS_SIN_ANOTAR) {
            boolean quedaAlgoPorAnotar = controllers().stream()
                    .filter(c -> modulo.equals(modulo(c)))
                    .flatMap(c -> handlersDe(c).stream())
                    .anyMatch(h -> !declaraAutorizacion(h));
            if (!quedaAlgoPorAnotar) {
                yaTerminados.add(modulo);
            }
        }
        assertThat(yaTerminados)
                .as("estos modulos ya tienen todos sus endpoints declarados: sacarlos de "
                        + "MODULOS_SIN_ANOTAR para que el test los proteja de verdad")
                .isEmpty();
    }

    @Test
    @DisplayName("los endpoints publicos explican por que pueden serlo")
    void everyPublicEndpointIsJustified() {
        Set<String> sinJustificar = new TreeSet<>();
        for (JavaClass controller : controllers()) {
            for (JavaMethod handler : handlersDe(controller)) {
                handler.tryGetAnnotationOfType(PublicEndpoint.class)
                        .filter(anotacion -> anotacion.value().isBlank())
                        .ifPresent(anotacion -> sinJustificar.add(identidad(handler)));
            }
        }
        assertThat(sinJustificar)
                .as("un endpoint publico sin justificacion escrita es el que nadie se anima "
                        + "a cerrar despues (ver @PublicEndpoint)")
                .isEmpty();
    }

    private static List<JavaClass> controllers() {
        List<JavaClass> encontrados = new ArrayList<>();
        for (JavaClass clase : CLASES) {
            if (clase.getSimpleName().endsWith("Controller")
                    && clase.getPackageName().startsWith("com.renaser.os.")) {
                encontrados.add(clase);
            }
        }
        return encontrados;
    }

    private static List<JavaMethod> handlersDe(JavaClass controller) {
        return controller.getMethods().stream()
                .filter(EndpointAuthorizationDeclarationTest::esHandlerHttp)
                .toList();
    }

    private static boolean esHandlerHttp(JavaMethod metodo) {
        return ANOTACIONES_DE_RUTA.stream().anyMatch(metodo::isAnnotatedWith);
    }

    private static boolean declaraAutorizacion(JavaMethod handler) {
        return handler.isAnnotatedWith(RequiresPermission.class)
                || handler.isAnnotatedWith(PublicEndpoint.class)
                || handler.getOwner().isAnnotatedWith(RequiresPermission.class)
                || handler.getOwner().isAnnotatedWith(PublicEndpoint.class);
    }

    private static String modulo(JavaClass controller) {
        return controller.getPackageName().substring("com.renaser.os.".length()).split("\\.")[0];
    }

    private static String identidad(JavaMethod handler) {
        return handler.getOwner().getSimpleName() + "#" + handler.getName();
    }
}
