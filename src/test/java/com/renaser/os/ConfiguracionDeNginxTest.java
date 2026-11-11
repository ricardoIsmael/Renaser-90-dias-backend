package com.renaser.os;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La configuración de nginx que genera {@code scripts/despliegue/desplegar-backend.sh} y lleva el CD a la instancia
 * (spec 050 tramo A2, FR-006 a FR-008). Es una prueba estática sobre la plantilla del script: que nginx rechace lo
 * que no trae la cabecera, deje pasar el health del CD y no anote secretos lo ensaya
 * {@code infra/cloudfront/ensayo-local.sh} con un nginx de verdad.
 */
class ConfiguracionDeNginxTest {

    private static final Path SCRIPT = Path.of("scripts/despliegue/desplegar-backend.sh");
    private static final Path CLOUDFRONT = Path.of("infra/cloudfront/cabecera-de-origen.sh");

    private static String script() throws IOException {
        return Files.readString(SCRIPT);
    }

    /** La plantilla de nginx: lo que hay entre {@code <<'FIN_CONF'} y {@code FIN_CONF} dentro del script. */
    private static String plantilla() throws IOException {
        String texto = script();
        int desde = texto.indexOf("<<'FIN_CONF'");
        assertThat(desde).as("el script ya no trae la plantilla <<'FIN_CONF'").isPositive();
        int inicio = texto.indexOf('\n', desde) + 1;
        return texto.substring(inicio, texto.indexOf("\nFIN_CONF\n", inicio));
    }

    /**
     * La función que escribe la configuración: no se puede cortar en la primera llave de la primera columna porque la
     * plantilla de nginx que lleva dentro tiene las suyas. Termina donde empieza la función siguiente.
     */
    private static String escribirConfig() throws IOException {
        String texto = script();
        int desde = texto.indexOf("escribir_config_proxy() {");
        assertThat(desde).isGreaterThanOrEqualTo(0);
        return texto.substring(desde, texto.indexOf("\n# $1 = nombre, $2 = puerto del host", desde));
    }

    private static String funcion(String texto, String nombre) {
        int desde = texto.indexOf(nombre + "() {");
        assertThat(desde).as("no encuentro la función %s", nombre).isGreaterThanOrEqualTo(0);
        return texto.substring(desde, texto.indexOf("\n}\n", desde) + 3);
    }

    @Test
    // spec 050 FR-006
    @DisplayName("rechaza con 403 y el código ORIGEN_NO_PERMITIDO lo que no trae la cabecera con el valor vigente o el anterior")
    void rechazaLoQueNoTraeLaCabecera() throws IOException {
        String conf = plantilla();
        assertThat(conf).contains("map $http_x_origen_renaser $origen_con_secreto {");
        assertThat(conf).contains("__ORIGEN_SECRETOS__");
        assertThat(conf).contains("if ($origen_rechazar) {").contains("rewrite ^ /__origen_no_permitido last;");
        assertThat(conf).contains("location = /__origen_no_permitido {").contains("internal;");
        assertThat(conf).contains("return 403 '{\"message\":\"No autorizado\",\"codigo\":\"ORIGEN_NO_PERMITIDO\"}';");
        assertThat(conf).contains("default_type application/json;");
        // El script arma el bloque con el vigente y el anterior, los dos desde Parameter Store.
        String secretos = funcion(script(), "bloque_de_secretos");
        assertThat(secretos).contains("origen-secreto origen-secreto-anterior").contains("parametro \"$nombre\"");
    }

    @Test
    // spec 050 FR-006
    @DisplayName("la cabecera que pone CloudFront es la que lee nginx")
    void laCabeceraEsLaMisma() throws IOException {
        assertThat(Files.readString(CLOUDFRONT)).contains("CABECERA=X-Origen-Renaser");
        assertThat(plantilla()).contains("$http_x_origen_renaser");
    }

    @Test
    // spec 050 FR-006
    @DisplayName("el health del CD sigue pasando: el propio host y la red de Docker quedan exentos, y el CD sigue comprobando por ahí")
    void elHealthDelCdPasa() throws IOException {
        String conf = plantilla();
        assertThat(conf).contains("geo $origen_de_operacion {").contains("127.0.0.1/32 1;").contains("__ORIGEN_IPS__");
        assertThat(script()).as("Docker entrega lo del host con la IP de la pasarela de la red, no con 127.0.0.1: se exime su subred")
                .contains("docker network inspect \"$RED\"");
        assertThat(script()).as("el CD comprueba la salud del proxy por el loopback: si esto cambia, revisa la exención del origen")
                .contains("curl -fsS --max-time 3 \"http://127.0.0.1:$1/actuator/health\"");
        assertThat(script()).as("y la del contenedor nuevo, directo por su IP, sin pasar por nginx")
                .contains("curl -fsS --max-time 3 \"http://$ip:$PUERTO/actuator/health\"");
        // Las IP de operacion salen de Parameter Store (nada en el repositorio) y se validan.
        String operacion = funcion(script(), "bloque_de_ips_de_operacion");
        assertThat(operacion).contains("parametro origen-ips-directas").contains("ipv4_valida");
    }

    @Test
    // spec 050 FR-008
    @DisplayName("el modo de partida es registrar (anota, no rechaza), exigir es a propósito y sin secretos o sin subred cae a registrar")
    void modoRegistrarPrimero() throws IOException {
        String escribir = escribirConfig();
        assertThat(escribir).contains("modo=${modo:-registrar}");
        assertThat(escribir).contains("modo=${ORIGEN_MODO:-$(parametro origen-modo)}");
        assertThat(escribir).contains("if [ \"$modo\" = exigir ]; then rechazar=1; fi");
        assertThat(escribir).contains("rechazar=0\n");
        assertThat(escribir).contains("[ \"$n_secretos\" -eq 0 ] || [ \"$n_subredes\" -eq 0 ]").contains("modo=registrar");
        assertThat(plantilla()).contains("map $origen_permitido $origen_rechazar {").contains("0       __ORIGEN_RECHAZAR__;");
    }

    @Test
    // spec 050 FR-008
    @DisplayName("lo que no trae la cabecera queda anotado, sin secretos, sin cabeceras y sin los parámetros de la URL")
    void anotaSinFiltrarSecretos() throws IOException {
        String conf = plantilla();
        assertThat(conf).contains("access_log /dev/stdout origen if=$origen_a_anotar;");
        String linea = conf.lines().filter(l -> l.startsWith("log_format origen")).findFirst().orElseThrow();
        assertThat(linea).contains("origen-sin-cabecera").contains("$remote_addr");
        assertThat(linea).doesNotContain("$http_").doesNotContain("$args").doesNotContain("$query_string")
                .doesNotContain("$request_uri").doesNotContain("$request ").doesNotContain("$request'");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("el secreto no entra al repositorio ni a la salida, y un valor raro de Parameter Store no entra al archivo de nginx")
    void sinSecretos() throws IOException {
        String texto = script();
        assertThat(texto).doesNotContainPattern("\\b[0-9a-fA-F]{32,}\\b");
        assertThat(funcion(texto, "bloque_de_secretos")).contains("^[A-Za-z0-9]{32,128}$").doesNotContain("echo \"$valor\"");
        String escribir = escribirConfig();
        assertThat(escribir).contains("umask 077").contains("chmod 600 \"$DIR_PROXY/renaser.conf\"");
        assertThat(escribir).as("el script solo cuenta los secretos, nunca los imprime").contains("secretos aceptados=$n_secretos");
        assertThat(texto).doesNotContainPattern("\\bset -x\\b");
    }

    @Test
    // spec 050 FR-006
    @DisplayName("la configuración nueva se prueba con nginx -t antes de reemplazar la que anda, y SOLO_PROXY falla si no es válida")
    void validaAntesDeReemplazar() throws IOException {
        String texto = script();
        String escribir = escribirConfig();
        assertThat(escribir.indexOf("config_de_nginx_valida")).isLessThan(escribir.indexOf("cat \"$DIR_PROXY/renaser.conf.nuevo\" > \"$DIR_PROXY/renaser.conf\""));
        assertThat(escribir).contains("return 2");
        assertThat(funcion(texto, "config_de_nginx_valida")).contains("nginx -t").contains("--network none");
        assertThat(texto).contains("*) echo \"ERROR: la configuracion nueva de nginx no es valida");
        // Un secreto de 64 caracteres no entra en el cubo por omisión de un `map` (lo cazó el ensayo local): va antes del primer map.
        String conf = plantilla();
        assertThat(conf).contains("map_hash_bucket_size 128;");
        assertThat(conf.indexOf("map_hash_bucket_size 128;")).isLessThan(conf.indexOf("\nmap "));
    }

    @Test
    // spec 050 FR-007
    @DisplayName("SOLO_PROXY reescribe y recarga nginx sin tocar el backend: así se rota el secreto sin corte")
    void soloProxy() throws IOException {
        String texto = script();
        assertThat(texto).contains("SOLO_PROXY=${SOLO_PROXY:-no}");
        int soloProxy = texto.indexOf("if [ \"$SOLO_PROXY\" = si ]; then\n  log \"Solo nginx");
        assertThat(soloProxy).isPositive();
        assertThat(soloProxy).as("SOLO_PROXY va antes de tocar imágenes o contenedores del backend")
                .isLessThan(texto.indexOf("log \"Limpieza de imagenes viejas del backend\""));
        String bloque = texto.substring(soloProxy, texto.indexOf("\nfi\n", soloProxy));
        assertThat(bloque).doesNotContain("docker rm").doesNotContain("correr_backend").doesNotContain("docker pull");
    }

    @Test
    @DisplayName("nginx sigue siendo transparente: no toca X-Forwarded-For (E-149), conserva el Host y el WebSocket")
    void siguePorTransparente() throws IOException {
        String conf = plantilla();
        assertThat(conf).doesNotContain("X-Forwarded-For").doesNotContain("X-Real-IP");
        assertThat(conf).contains("proxy_set_header Host $http_host;").contains("proxy_set_header Upgrade $http_upgrade;");
        assertThat(conf).contains("proxy_request_buffering off;").contains("proxy_buffering off;");
    }

    @Test
    @DisplayName("el cuerpo que el CD manda por SSM (el script más la config de Alloy en base64) no se acerca al límite de SSM")
    void elCuerpoPorSsmNoCreceSinAviso() throws IOException {
        // El CD manda `desplegar-backend.sh` y `config.alloy` en base64 como UN solo parámetro de `ssm send-command`. El límite exacto de
        // SSM no está confirmado por AWS (un blog habla de 100 KB; hoy se mandan ~34 KB): este techo de 60 KB avisa mucho antes.
        int alloy = java.util.Base64.getEncoder().encode(Files.readAllBytes(Path.of("infra/observabilidad/alloy/config.alloy"))).length;
        long total = Files.size(SCRIPT) + alloy + 1_000;
        assertThat(total).as("el cuerpo del despliegue por SSM pasó de 60 KB: reduce el script o súbelo a S3 antes de seguir").isLessThan(60_000L);
    }

    @Test
    @DisplayName("el script de despliegue tiene sintaxis válida de bash")
    void sintaxisValida() throws Exception {
        Process proceso = new ProcessBuilder("bash", "-n", SCRIPT.toString()).redirectErrorStream(true).start();
        String salida = new String(proceso.getInputStream().readAllBytes());
        assertThat(proceso.waitFor()).as("bash -n %s: %s", SCRIPT, salida).isZero();
    }
}
