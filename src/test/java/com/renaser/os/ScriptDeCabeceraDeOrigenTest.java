package com.renaser.os;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El script de la cabecera de origen (spec 050 tramo A2) cumple lo que la spec exige de todo script de
 * infraestructura (FR-011) y los pasos de FR-007. Es una prueba estática: la distribución real vive en la cuenta de
 * AWS del dueño y {@code infra/cloudfront/ensayo-local.sh} corre el script de punta a punta contra un nginx de verdad
 * y un AWS de mentira.
 */
class ScriptDeCabeceraDeOrigenTest {

    private static final Path SCRIPT = Path.of("infra/cloudfront/cabecera-de-origen.sh");
    private static final Path ENSAYO = Path.of("infra/cloudfront/ensayo-local.sh");

    private static String leer(Path ruta) throws IOException {
        return Files.readString(ruta);
    }

    private static String sinComentarios(String texto) {
        return texto.lines().filter(l -> !l.stripLeading().startsWith("#")).reduce("", (a, b) -> a + b + "\n");
    }

    private static String funcion(String texto, String nombre) {
        int desde = texto.indexOf(nombre + "() {");
        assertThat(desde).as("no encuentro la función %s", nombre).isGreaterThanOrEqualTo(0);
        return texto.substring(desde, texto.indexOf("\n}\n", desde) + 3);
    }

    @Test
    // spec 050 FR-011
    @DisplayName("los scripts de la cabecera tienen sintaxis válida de bash")
    void sintaxisValida() throws Exception {
        for (Path ruta : List.of(SCRIPT, ENSAYO)) {
            Process proceso = new ProcessBuilder("bash", "-n", ruta.toString()).redirectErrorStream(true).start();
            String salida = new String(proceso.getInputStream().readAllBytes());
            assertThat(proceso.waitFor()).as("bash -n %s: %s", ruta, salida).isZero();
        }
    }

    @Test
    // spec 050 FR-011
    @DisplayName("falla ante cualquier error y corre en simulacro salvo que se le pase --aplicar")
    void simulacroPorOmision() throws IOException {
        String script = leer(SCRIPT);
        assertThat(script).contains("set -euo pipefail").contains("APLICAR=no").contains("--aplicar) APLICAR=si");
        assertThat(script).as("--simular le gana a --aplicar").contains("[ \"$SIMULAR\" = si ] && APLICAR=no");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("todo AWS va por aws_prod (perfil prod), la cuenta se verifica y ningún id está escrito en un repositorio público")
    void perfilYCuenta() throws IOException {
        String script = leer(SCRIPT);
        assertThat(script).contains("PERFIL=prod").contains("aws_prod() { aws \"$@\" --profile \"$PERFIL\"; }");
        List<String> directas = sinComentarios(script).lines()
                .filter(l -> l.matches(".*\\baws [a-z].*") && !l.contains("aws_prod() {") && !l.contains("for h in aws") && !l.stripLeading().startsWith("echo")).toList();
        assertThat(directas).as("llamadas a aws que no pasan por aws_prod (sin perfil)").isEmpty();
        assertThat(script).contains("${RENASER_CUENTA_AWS:?").contains("${RENASER_DISTRIBUCION:?").contains("${RENASER_INSTANCIA:?");
        assertThat(script).contains("\"$CUENTA\" = \"$RENASER_CUENTA_AWS\"");
        assertThat(script).doesNotContainPattern("(?<![0-9])[0-9]{12}(?![0-9])");
        assertThat(script).doesNotContainPattern("\\bE[0-9A-Z]{12,13}\\b");
        assertThat(script).doesNotContainPattern("\\bi-[0-9a-f]{8,17}\\b");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("el secreto nunca va por argumentos ni a la pantalla: --rawfile, archivos con permiso 600 y ningún set -x")
    void sinSecretosEnPantalla() throws IOException {
        String script = sinComentarios(leer(SCRIPT));
        assertThat(script).contains("--rawfile").contains("umask 077");
        assertThat(script).as("--arg v dejaría el secreto en la lista de procesos").doesNotContainPattern("--arg v\\b");
        assertThat(script).doesNotContainPattern("\\bset -x\\b").doesNotContainPattern("echo \"\\$\\(cat \"\\$TMP/(secreto|nuevo|viejo)\"\\)\"");
        for (Path ruta : List.of(SCRIPT, ENSAYO)) {
            assertThat(leer(ruta)).as("%s no lleva una cadena larga en hexadecimal", ruta).doesNotContainPattern("\\b[0-9a-fA-F]{32,}\\b");
        }
    }

    @Test
    // spec 050 FR-011
    @DisplayName("la distribución se cambia con el ETag, se espera a que se despliegue y antes se guarda un respaldo con permiso 600")
    void distribucionConEtagYRespaldo() throws IOException {
        String script = leer(SCRIPT);
        assertThat(script).contains("get-distribution-config").contains("--if-match \"$ETAG\"").contains("update-distribution");
        assertThat(script).contains("wait distribution-deployed");
        String respaldar = funcion(script, "respaldar_distribucion");
        assertThat(respaldar).contains("cp \"$TMP/config.json\" \"$RESPALDO\"").contains("chmod 600 \"$RESPALDO\"").contains("chmod 700");
        assertThat(script).as("el respaldo vive fuera del repositorio").contains("RESPALDOS=${RENASER_RESPALDOS:-$HOME/.renaser/respaldos}");
    }

    @Test
    // spec 050 FR-007
    @DisplayName("la rotación sigue el orden que evita el corte: nginx acepta los dos, CloudFront cambia, se espera, nginx suelta el viejo")
    void ordenDeLaRotacion() throws IOException {
        String rotar = funcion(leer(SCRIPT), "paso_rotar");
        int dosSecretos = rotar.indexOf("guardar_secreto origen-secreto-anterior");
        int nginxAcepta = rotar.indexOf("nginx_al_dia", dosSecretos);
        int cloudfront = rotar.indexOf("poner_cabecera \"$TMP/nuevo\"");
        int comprueba = rotar.indexOf("comprobar_por_cloudfront", cloudfront);
        int espera = rotar.indexOf("sleep \"$ESPERA_ROTACION\"");
        int borraViejo = rotar.indexOf("delete-parameter");
        int nginxSuelta = rotar.indexOf("nginx_al_dia", borraViejo);
        assertThat(List.of(dosSecretos, nginxAcepta, cloudfront, comprueba, espera, borraViejo, nginxSuelta))
                .as("posiciones de los pasos de la rotación").isSorted().doesNotContain(-1);
    }

    @Test
    // spec 050 FR-008
    @DisplayName("exigir se niega si hubo pedidos sin cabecera, comprueba por CloudFront y vuelve a registrar si falla")
    void exigirConRed() throws IOException {
        String exigir = funcion(leer(SCRIPT), "paso_exigir");
        assertThat(exigir).contains("pedidos_sin_cabecera").contains("--forzar").contains("TOTAL=");
        assertThat(exigir).contains("guardar_texto origen-modo exigir").contains("comprobar_por_cloudfront");
        assertThat(exigir.indexOf("comprobar_por_cloudfront")).isLessThan(exigir.lastIndexOf("guardar_texto origen-modo registrar"));
    }

    @Test
    // spec 050 FR-011
    @DisplayName("la vuelta atrás pasa nginx a registrar ANTES de quitar la cabecera, y solo quita la cabecera (no pisa lo demás de la distribución)")
    void vueltaAtras() throws IOException {
        String script = leer(SCRIPT);
        String revertir = funcion(script, "revertir");
        assertThat(revertir.indexOf("guardar_texto origen-modo registrar")).isLessThan(revertir.indexOf("quitar_cabecera"));
        assertThat(revertir.indexOf("nginx_al_dia")).isLessThan(revertir.indexOf("quitar_cabecera"));
        String quitar = funcion(script, "quitar_cabecera");
        assertThat(quitar).contains("select((.HeaderName | ascii_downcase) != ($n | ascii_downcase))").doesNotContain("RESPALDO");
        assertThat(script).as("la salida del ensayo lo dice").contains("--revertir");
        assertThat(leer(ENSAYO)).contains("== 8. vuelta atras: nginx a registrar y despues se quita la cabecera ==");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("la clave y los modos viven en Parameter Store: SecureString para los secretos, texto para el modo")
    void parametros() throws IOException {
        String script = leer(SCRIPT);
        assertThat(script).contains("origen-secreto").contains("origen-secreto-anterior").contains("origen-modo").contains("SecureString");
        assertThat(script).contains("Overwrite: true").contains("--type String");
    }
}
