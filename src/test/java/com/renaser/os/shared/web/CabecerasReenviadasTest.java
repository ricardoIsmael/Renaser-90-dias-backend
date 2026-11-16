package com.renaser.os.shared.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-678. Con {@code server.forward-headers-strategy=framework} el {@code ForwardedHeaderFilter} de Spring reescribe la
 * direccion remota con el primer valor de {@code X-Forwarded-For} y ESCONDE esas cabeceras de todo lo que viene despues:
 * {@code DireccionIpDelCliente} ya no podria leer el valor que agrega CloudFront. Esta prueba falla si alguien vuelve a
 * poner esa estrategia (o {@code native}), o si la deja en una variable de entorno que el servidor pueda cambiar.
 *
 * <p>Se lee el yaml tal cual esta escrito, sin arrancar Spring: lo que cuenta es lo que se despliega. Se lee del archivo
 * de {@code src/main/resources} y no del classpath: {@code src/test/resources/application.yaml} REEMPLAZA al de main
 * (mismo nombre, gana el de test), asi que desde el classpath se leeria el de las pruebas. Ese archivo de pruebas
 * espeja el valor para que los IT corran con la configuracion de produccion, y esta clase comprueba que lo espeje.
 */
class CabecerasReenviadasTest {

    private static final String MAIN = "src/main/resources/";
    private static final String PRUEBAS = "src/test/resources/";

    /** El valor de la clave en ese yaml, o null si no esta. Falla si el archivo no existe: una guarda que no lee nada no guarda. */
    private static Object propiedad(String ruta, String clave) throws IOException {
        FileSystemResource yaml = new FileSystemResource(ruta);
        assertThat(yaml.exists()).as("no existe %s (las pruebas corren con la raiz del proyecto como carpeta de trabajo)", ruta).isTrue();
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader().load(ruta, yaml);
        return fuentes.stream().map(f -> f.getProperty(clave)).filter(v -> v != null).findFirst().orElse(null);
    }

    @Test
    @DisplayName("server.forward-headers-strategy es 'none' escrito a mano: ni Spring ni Tomcat reescriben la IP desde cabeceras")
    void laEstrategiaDeCabecerasReenviadasEsNone() throws IOException {
        assertThat(propiedad(MAIN + "application.yaml", "server.forward-headers-strategy"))
                .as("""
                        server.forward-headers-strategy tiene que ser exactamente `none` en application.yaml, sin variable de entorno. \
                        Con `framework` o `native` la direccion remota se reescribe desde X-Forwarded-For (el primer valor) y la cabecera \
                        queda escondida para DireccionIpDelCliente, que toma el valor que agrega CloudFront (E-678, D-342).""")
                .isEqualTo("none");
    }

    @Test
    @DisplayName("el application.yaml de las pruebas espeja el valor de main, para que los IT corran con la configuracion de produccion")
    void elYamlDeLasPruebasLoEspeja() throws IOException {
        assertThat(propiedad(PRUEBAS + "application.yaml", "server.forward-headers-strategy")).isEqualTo("none");
    }

    @Test
    @DisplayName("el perfil de produccion tampoco la cambia")
    void produccionNoLaCambia() throws IOException {
        assertThat(propiedad(MAIN + "application-prod.yaml", "server.forward-headers-strategy")).isNull();
    }

    @Test
    @DisplayName("la cabecera CloudFront-Viewer-Address nace sin confianza: la politica Managed-AllViewer no la agrega")
    void laCabeceraDelVisitanteNaceApagada() throws IOException {
        assertThat(propiedad(MAIN + "application.yaml", "renaser.security.ip-del-cliente.confiar-en-cloudfront-viewer-address"))
                .isEqualTo(false);
    }
}
