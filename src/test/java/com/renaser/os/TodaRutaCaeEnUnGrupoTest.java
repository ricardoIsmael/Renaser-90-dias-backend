package com.renaser.os;

import com.renaser.os.shared.domain.ClasificadorDeGrupos;
import com.renaser.os.shared.domain.GrupoDeLimite;
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

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Toda ruta de {@code /api/v1} cae en un grupo del límite por cuenta o está exenta por escrito (spec 050, FR-014).
 *
 * <p><b>Qué cubre.</b> {@link ClasificadorDeGrupos} termina en «escritura» o «lectura» para lo que no reconoce, así que ninguna ruta
 * queda sin grupo por accidente; el riesgo es el contrario: que una ruta nueva caiga en el grupo equivocado sin que nadie lo decida
 * (una subida contada como lectura, una exportación como escritura, un endpoint público que nadie limitó). Por eso esta prueba recorre
 * los controllers de verdad y exige:
 * <ul>
 *   <li>que <b>lo exento</b> sea exactamente lo público ({@code @PublicEndpoint}) y que un endpoint público nuevo no pase sin que
 *       alguien lo exente a propósito o lo limite;</li>
 *   <li>que toda ruta de subida ({@code …/upload-url}, {@code …/presign}), de exportación ({@code …/export.csv},
 *       {@code …/exportar.csv}), de mensajes del chat, del Muro, de SER y de administración caiga en su grupo, y que cada grupo
 *       tenga por lo menos una ruta real (si se renombra la ruta, el grupo queda vacío y la prueba lo dice).</li>
 * </ul>
 * Si falla, el mensaje dice qué ruta es y dónde se arregla: {@code ClasificadorDeGrupos} y la tabla del plan (§5).
 */
class TodaRutaCaeEnUnGrupoTest {

    private record Ruta(String metodo, String ruta, boolean publica) {

        @Override
        public String toString() {
            return metodo + " " + ruta;
        }
    }

    /**
     * Endpoints {@code @PublicEndpoint} que el filtro SÍ cuenta, decididos a propósito: {@code POST /auth/logout} tiene sesión (Spring
     * Security la exige; se marca público solo para el interceptor de permisos) y cuenta como escritura.
     */
    private static final java.util.Set<String> PUBLICOS_QUE_SE_CUENTAN = java.util.Set.of("POST /api/v1/auth/logout");

    private static final List<Class<? extends Annotation>> MAPEOS = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class, PutMapping.class, PatchMapping.class, DeleteMapping.class);

    private static List<Ruta> rutasDeLaApi() {
        JavaClasses clases = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.renaser.os");
        List<Ruta> rutas = new ArrayList<>();
        for (JavaClass clase : clases) {
            for (JavaMethod metodo : clase.getMethods()) {
                rutas.addAll(rutasDe(clase, metodo));
            }
        }
        return rutas.stream().filter(r -> r.ruta().startsWith("/api/v1/")).toList();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static List<Ruta> rutasDe(JavaClass clase, JavaMethod metodo) {
        List<Ruta> rutas = new ArrayList<>();
        String base = clase.isAnnotatedWith(RequestMapping.class) ? primeraRuta(clase.getAnnotationOfType(RequestMapping.class)) : "";
        for (Class<? extends Annotation> mapeo : MAPEOS) {
            if (!metodo.isAnnotatedWith(mapeo)) {
                continue;
            }
            Annotation anotacion = (Annotation) metodo.getAnnotationOfType((Class) mapeo);
            String verbo = verboDe(mapeo, anotacion);
            for (String sufijo : rutasDeLaAnotacion(anotacion)) {
                String ruta = (base + sufijo).replaceAll("//+", "/").replaceAll("\\{[^}]+}", "x");
                rutas.add(new Ruta(verbo, ruta, metodo.isAnnotatedWith(PublicEndpoint.class)));
            }
        }
        return rutas;
    }

    private static String verboDe(Class<? extends Annotation> mapeo, Annotation anotacion) {
        if (mapeo == RequestMapping.class) {
            var metodos = ((RequestMapping) anotacion).method();
            return metodos.length == 0 ? "GET" : metodos[0].name();
        }
        return mapeo.getSimpleName().replace("Mapping", "").toUpperCase(java.util.Locale.ROOT);
    }

    private static String primeraRuta(RequestMapping anotacion) {
        String[] rutas = anotacion.value().length > 0 ? anotacion.value() : anotacion.path();
        return rutas.length == 0 ? "" : rutas[0];
    }

    private static List<String> rutasDeLaAnotacion(Annotation anotacion) {
        try {
            String[] valor = (String[]) anotacion.annotationType().getMethod("value").invoke(anotacion);
            String[] ruta = valor.length > 0 ? valor : (String[]) anotacion.annotationType().getMethod("path").invoke(anotacion);
            return ruta.length == 0 ? List.of("") : List.of(ruta);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No se pudo leer la ruta de " + anotacion, e);
        }
    }

    private static Optional<GrupoDeLimite> grupoDe(Ruta ruta) {
        return ClasificadorDeGrupos.grupoDe(ruta.metodo(), ruta.ruta());
    }

    // spec 050 FR-014
    @Test
    @DisplayName("lo exento es exactamente lo público: ninguna ruta con sesión queda sin contar y ningún endpoint público queda sin decidir")
    void loExentoEsLoPublico() {
        List<Ruta> rutas = rutasDeLaApi();
        assertThat(rutas).as("no se descubrió ninguna ruta").hasSizeGreaterThan(100);

        var exentasConSesion = new TreeSet<String>();
        var publicasSinDecidir = new TreeSet<String>();
        for (Ruta ruta : rutas) {
            boolean exenta = grupoDe(ruta).isEmpty();
            boolean limitadaPorSuOrigen = grupoDe(ruta).filter(g -> g == GrupoDeLimite.WEBHOOK_WHATSAPP).isPresent();
            if (exenta && !ruta.publica()) {
                exentasConSesion.add(ruta.toString());
            }
            if (ruta.publica() && !exenta && !limitadaPorSuOrigen && !PUBLICOS_QUE_SE_CUENTAN.contains(ruta.toString())) {
                publicasSinDecidir.add(ruta.toString());
            }
        }

        assertThat(exentasConSesion).as("""
                Estas rutas están exentas del límite por cuenta en ClasificadorDeGrupos.EXENTAS pero no son @PublicEndpoint: tienen \
                sesión, así que sí se cuentan. Quita la exención.""").isEmpty();
        assertThat(publicasSinDecidir).as("""
                Estos endpoints son @PublicEndpoint y el límite por cuenta los cuenta como cualquier otro (por su origen, sin sesión). \
                Si ya tienen su límite propio (como el login), agrégalos a ClasificadorDeGrupos.EXENTAS; si tienen sesión y deben \
                contar, agrega su ruta a PUBLICOS_QUE_SE_CUENTAN en esta prueba.""").isEmpty();
    }

    // spec 050 FR-014
    @Test
    @DisplayName("subidas, exportaciones, mensajes del chat, Muro, SER y administración caen en su grupo, y cada grupo tiene rutas reales")
    void cadaFamiliaCaeEnSuGrupoYCadaGrupoTieneRutas() {
        List<Ruta> rutas = rutasDeLaApi();
        Map<GrupoDeLimite, List<String>> porGrupo = new EnumMap<>(GrupoDeLimite.class);
        var mal = new TreeSet<String>();
        for (Ruta ruta : rutas) {
            grupoDe(ruta).ifPresent(g -> porGrupo.computeIfAbsent(g, k -> new ArrayList<>()).add(ruta.toString()));
            comprobarFamilias(ruta, mal);
        }

        assertThat(mal).as("Estas rutas no cayeron en el grupo que su nombre dice: arregla ClasificadorDeGrupos (plan §5)").isEmpty();
        for (GrupoDeLimite grupo : GrupoDeLimite.values()) {
            assertThat(porGrupo.get(grupo)).as("el grupo %s no tiene ninguna ruta real: ¿se renombró la ruta?", grupo.clave())
                    .isNotNull().isNotEmpty();
        }
    }

    private static void comprobarFamilias(Ruta ruta, TreeSet<String> mal) {
        Optional<GrupoDeLimite> grupo = grupoDe(ruta);
        boolean esperadaSubida = ruta.ruta().contains("upload") || ruta.ruta().contains("presign");
        boolean esperadaExportacion = ruta.ruta().endsWith("/export.csv") || ruta.ruta().endsWith("/exportar.csv");
        if (esperadaSubida && !grupo.equals(Optional.of(GrupoDeLimite.SUBIDAS))) {
            mal.add(ruta + " debería ser subidas y es " + grupo);
        }
        if (esperadaExportacion && !grupo.equals(Optional.of(GrupoDeLimite.EXPORTACIONES))) {
            mal.add(ruta + " debería ser exportaciones y es " + grupo);
        }
        if (ruta.ruta().startsWith("/api/v1/admin/") && !esperadaSubida && !esperadaExportacion
                && !grupo.equals(Optional.of(GrupoDeLimite.ADMIN))) {
            mal.add(ruta + " debería ser admin y es " + grupo);
        }
        if (ruta.metodo().equals("POST") && ruta.ruta().startsWith("/api/v1/renasia/") && !grupo.equals(Optional.of(GrupoDeLimite.SER))) {
            mal.add(ruta + " debería ser ser y es " + grupo);
        }
        if (ruta.metodo().equals("POST") && ruta.ruta().matches("/api/v1/chat/conversations/x/messages(/.*)?")
                && !grupo.equals(Optional.of(GrupoDeLimite.CHAT))) {
            mal.add(ruta + " debería ser chat y es " + grupo);
        }
    }

    // spec 050 FR-013
    @Test
    @DisplayName("publicar y comentar en el Muro son el grupo muro, y solo eso")
    void elMuroSonDosRutas() {
        var delMuro = rutasDeLaApi().stream().filter(r -> grupoDe(r).equals(Optional.of(GrupoDeLimite.MURO))).map(Ruta::toString).toList();

        assertThat(delMuro).containsExactlyInAnyOrder("POST /api/v1/wall", "POST /api/v1/wall/x/comments");
    }
}
