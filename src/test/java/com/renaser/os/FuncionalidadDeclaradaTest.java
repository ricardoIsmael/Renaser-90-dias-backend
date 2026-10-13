package com.renaser.os;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.SinInterruptor;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.annotation.Annotation;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La prueba de arquitectura de la spec 002 (FR-009, SC-002; contrato §9): <b>todo endpoint dice a qué funcionalidad
 * pertenece</b> ({@link Funcionalidad}, en el método o en su clase) <b>o que no tiene interruptor</b>
 * ({@link SinInterruptor}, con motivo). Sin esto, un endpoint nuevo quedaría fuera del alcance de Soporte TI: no se
 * podría apagar sin desplegar código, que es lo que la spec existe para evitar.
 *
 * <p>Además, toda clave anotada tiene que existir en el catálogo: una clave mal escrita sería un interruptor que no
 * apaga nada (la evaluación trata una clave desconocida como «sin interruptor»).
 *
 * <p>Se reconocen los controllers por su anotación ({@code @RestController} o {@code @Controller}), no por el nombre,
 * para que un controller nuevo con otro sufijo no se escape.
 */
class FuncionalidadDeclaradaTest {

    private static final List<Class<? extends Annotation>> ANOTACIONES_DE_RUTA = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class,
            PutMapping.class, PatchMapping.class, DeleteMapping.class);

    private static final JavaClasses CLASES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.renaser.os");

    private final CatalogoDeFuncionalidades catalogo = CatalogoDeFuncionalidades.inicial();

    @Test
    @DisplayName("todo endpoint declara @Funcionalidad o @SinInterruptor (en el método o en su clase)")
    void todoEndpointDeclaraSuFuncionalidad() {
        Set<String> sinDeclarar = new TreeSet<>();
        handlers().filter(handler -> !declara(handler)).forEach(handler -> sinDeclarar.add(identidad(handler)));

        assertThat(controllers()).as("la prueba tiene que encontrar los controllers").hasSizeGreaterThan(100);
        assertThat(sinDeclarar)
                .as("endpoints sin funcionalidad (spec 002, FR-009): agrega @Funcionalidad(\"clave\") con una clave del "
                        + "catálogo de plataforma, o @SinInterruptor(motivo = ...) si es anti-bloqueo")
                .isEmpty();
    }

    @Test
    @DisplayName("toda clave de @Funcionalidad existe en el catálogo")
    void todaClaveExisteEnElCatalogo() {
        Set<String> desconocidas = new TreeSet<>();
        for (JavaClass controller : controllers()) {
            controller.tryGetAnnotationOfType(Funcionalidad.class)
                    .filter(anotacion -> !catalogo.existe(anotacion.value()))
                    .ifPresent(anotacion -> desconocidas.add(controller.getSimpleName() + " -> " + anotacion.value()));
        }
        handlers().forEach(handler -> handler.tryGetAnnotationOfType(Funcionalidad.class)
                .filter(anotacion -> !catalogo.existe(anotacion.value()))
                .ifPresent(anotacion -> desconocidas.add(identidad(handler) + " -> " + anotacion.value())));

        assertThat(desconocidas).as("claves que no están en CatalogoDeFuncionalidades").isEmpty();
    }

    @Test
    @DisplayName("ningún handler es @Funcionalidad y @SinInterruptor a la vez, y todo @SinInterruptor tiene motivo")
    void sinAmbiguedadesYConMotivo() {
        Set<String> problemas = new TreeSet<>();
        handlers().forEach(handler -> {
            if (handler.isAnnotatedWith(Funcionalidad.class) && handler.isAnnotatedWith(SinInterruptor.class)) {
                problemas.add(identidad(handler) + ": las dos anotaciones");
            }
            handler.tryGetAnnotationOfType(SinInterruptor.class).filter(a -> a.motivo().isBlank())
                    .ifPresent(a -> problemas.add(identidad(handler) + ": @SinInterruptor sin motivo"));
        });
        for (JavaClass controller : controllers()) {
            if (controller.isAnnotatedWith(Funcionalidad.class) && controller.isAnnotatedWith(SinInterruptor.class)) {
                problemas.add(controller.getSimpleName() + ": las dos anotaciones en la clase");
            }
            controller.tryGetAnnotationOfType(SinInterruptor.class).filter(a -> a.motivo().isBlank())
                    .ifPresent(a -> problemas.add(controller.getSimpleName() + ": @SinInterruptor sin motivo"));
        }

        assertThat(problemas).isEmpty();
    }

    private static List<JavaClass> controllers() {
        return CLASES.stream()
                .filter(clase -> clase.isAnnotatedWith(RestController.class) || clase.isAnnotatedWith(Controller.class))
                .toList();
    }

    private static Stream<JavaMethod> handlers() {
        return controllers().stream().flatMap(controller -> controller.getMethods().stream())
                .filter(metodo -> ANOTACIONES_DE_RUTA.stream().anyMatch(metodo::isAnnotatedWith));
    }

    private static boolean declara(JavaMethod handler) {
        return handler.isAnnotatedWith(Funcionalidad.class) || handler.isAnnotatedWith(SinInterruptor.class)
                || handler.getOwner().isAnnotatedWith(Funcionalidad.class)
                || handler.getOwner().isAnnotatedWith(SinInterruptor.class);
    }

    private static String identidad(JavaMethod handler) {
        return handler.getOwner().getSimpleName() + "#" + handler.getName();
    }
}
