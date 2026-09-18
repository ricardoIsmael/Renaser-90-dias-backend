package com.renaser.os;

import com.renaser.os.shared.web.security.PublicEndpoint;
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
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import org.springframework.http.server.PathContainer;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda ruta de controller tiene que estar CUBIERTA por el filtro de seguridad.
 *
 * <p><b>El agujero que cierra (2026-09-18).</b> {@code SecurityConfig} no aplica autenticación por
 * omisión: enumera patrones y termina en {@code .anyRequest().permitAll()}. Una ruta que no entra en
 * esa lista queda abierta, y ahí {@code @ActorAutenticado} cae al header {@code X-Actor-Id} que
 * escribe el propio cliente — o sea que todos los guards de esa ruta pasan a comprobar una identidad
 * elegida por quien llama. Los UUID no son secretos: varias respuestas los devuelven.
 *
 * <p>Ya se habían cerrado a mano tres huecos de esta forma (2026-09-06 y 2026-09-11), y aun así se
 * escaparon dos más: {@code /api/v1/me/cells} —que el patrón {@code /api/v1/me/cell/**} NO cubre— y
 * {@code /api/v1/participants/**}, cuyo controller escribe las rutas enteras en cada método y por eso
 * no aparecía en ninguna búsqueda por prefijo.
 *
 * <p><b>Por qué hacía falta ESTA prueba y no alcanzaba la que había.</b>
 * {@code EndpointAuthorizationDeclarationTest} verifica que cada handler <i>declare</i> su
 * autorización. Las dos rutas la declaraban perfectamente. Lo que nadie verificaba es que el filtro
 * las alcance, que es una propiedad de la ruta, no de la anotación.
 *
 * <p><b>Por qué lee el archivo fuente.</b> Los patrones viven dentro de un lambda de
 * {@code HttpSecurity}; no hay forma de preguntárselos al bean sin levantar el contexto y sondear
 * cada ruta. Leer los literales es frágil ante un cambio de formato — y por eso la prueba falla
 * ruidosamente si deja de encontrarlos, en vez de dar por buena una lista vacía.
 */
class RutasCubiertasPorElFiltroTest {

    private static final Path SECURITY_CONFIG =
            Path.of("src/main/java/com/renaser/os/shared/web/SecurityConfig.java");

    /** Un `.requestMatchers("a", "b").authenticated()` puede abarcar varias líneas. */
    private static final Pattern BLOQUE_AUTENTICADO =
            Pattern.compile("\\.requestMatchers\\(([^;]*?)\\)\\s*(?://[^\\n]*\\n\\s*)?\\.authenticated\\(\\)",
                    Pattern.DOTALL);
    private static final Pattern LITERAL = Pattern.compile("\"([^\"]+)\"");

    private static final List<Class<? extends Annotation>> MAPEOS = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class,
            PutMapping.class, PatchMapping.class, DeleteMapping.class);

    @Test
    @DisplayName("Ninguna ruta de controller queda fuera del filtro de seguridad")
    void ningunaRutaQuedaFueraDelFiltro() throws IOException {
        List<PathPattern> protegidas = patronesAutenticados();
        // Si el formato de SecurityConfig cambiara, esta prueba no puede pasar en silencio con una
        // lista vacia: eso daria por cubiertas TODAS las rutas, que es lo contrario de lo que mide.
        assertThat(protegidas)
                .as("no se pudo leer ningun patron .authenticated() de SecurityConfig.java — "
                        + "cambio el formato y esta prueba quedo ciega")
                .hasSizeGreaterThan(20);

        var descubiertas = new TreeSet<String>();
        for (JavaClass clase : controllers()) {
            for (JavaMethod metodo : clase.getMethods()) {
                for (String ruta : rutasDe(clase, metodo)) {
                    if (!metodo.isAnnotatedWith(PublicEndpoint.class) && ruta.startsWith("/api/")) {
                        descubiertas.add(ruta);
                    }
                }
            }
        }
        assertThat(descubiertas).as("no se descubrio ninguna ruta").hasSizeGreaterThan(100);

        var sinCubrir = descubiertas.stream()
                .filter(ruta -> protegidas.stream().noneMatch(p -> p.matches(aRuta(ruta))))
                .toList();

        assertThat(sinCubrir)
                .as("""
                        Estas rutas no las alcanza ningun matcher .authenticated() de SecurityConfig \
                        y tampoco declaran @PublicEndpoint. Como la cadena termina en \
                        anyRequest().permitAll(), quedan accesibles SIN sesion, y ahi la identidad \
                        sale del header X-Actor-Id que manda el cliente. O se agregan a \
                        SecurityConfig, o se marcan @PublicEndpoint con su justificacion.""")
                .isEmpty();
    }

    private static List<PathPattern> patronesAutenticados() throws IOException {
        String fuente = Files.readString(SECURITY_CONFIG);
        var parser = new PathPatternParser();
        var patrones = new ArrayList<PathPattern>();
        Matcher bloque = BLOQUE_AUTENTICADO.matcher(fuente);
        while (bloque.find()) {
            Matcher literal = LITERAL.matcher(bloque.group(1));
            while (literal.find()) {
                patrones.add(parser.parse(literal.group(1)));
            }
        }
        return patrones;
    }

    private static PathContainer aRuta(String ruta) {
        // Las variables de ruta se sustituyen por un segmento concreto: el patron tiene que casar
        // por FORMA, y `{id}` no es un segmento que un cliente vaya a mandar.
        return PathContainer.parsePath(ruta.replaceAll("\\{[^}]+}", "x"));
    }

    private static JavaClasses controllers() {
        return new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.renaser.os");
    }

    /** Ruta completa = la de la clase (si tiene) + la del metodo. Ambas pueden faltar o ser varias. */
    private static List<String> rutasDe(JavaClass clase, JavaMethod metodo) {
        List<String> delMetodo = valoresDeMapeo(metodo);
        if (delMetodo.isEmpty()) {
            return List.of();
        }
        List<String> deLaClase = clase.isAnnotatedWith(RequestMapping.class)
                ? List.of(atributoRuta(clase.getAnnotationOfType(RequestMapping.class)))
                : List.of("");
        var completas = new ArrayList<String>();
        for (String base : deLaClase) {
            for (String sufijo : delMetodo) {
                String ruta = (base + sufijo).replaceAll("//+", "/");
                completas.add(ruta.isEmpty() ? "/" : ruta);
            }
        }
        return completas;
    }

    private static List<String> valoresDeMapeo(JavaMethod metodo) {
        for (Class<? extends Annotation> anotacion : MAPEOS) {
            if (metodo.isAnnotatedWith(anotacion)) {
                Annotation valor = metodo.getAnnotationOfType((Class) anotacion);
                String[] rutas = rutasDeAnotacion(valor);
                // Un @GetMapping sin ruta hereda la de la clase: se representa con la cadena vacia.
                return rutas.length == 0 ? List.of("") : List.of(rutas);
            }
        }
        return List.of();
    }

    private static String atributoRuta(RequestMapping anotacion) {
        String[] rutas = anotacion.value().length > 0 ? anotacion.value() : anotacion.path();
        return rutas.length == 0 ? "" : rutas[0];
    }

    private static String[] rutasDeAnotacion(Annotation anotacion) {
        try {
            String[] valor = (String[]) anotacion.annotationType().getMethod("value").invoke(anotacion);
            if (valor.length > 0) {
                return valor;
            }
            return (String[]) anotacion.annotationType().getMethod("path").invoke(anotacion);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo leer la ruta de " + anotacion, e);
        }
    }
}
