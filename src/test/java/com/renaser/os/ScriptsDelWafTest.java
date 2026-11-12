package com.renaser.os;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los scripts del WAF (spec 050 tramo B) cumplen lo que la spec exige de todo script de infraestructura (FR-011) y los
 * pasos de FR-002 y FR-005. Es una prueba estática: el Web ACL real vive en la cuenta de AWS del dueño y
 * {@code infra/waf/ensayo-local.sh} corre los scripts de punta a punta contra un AWS de mentira.
 */
class ScriptsDelWafTest {

    private static final Path DIR = Path.of("infra/waf");
    private static final Path COMUN = DIR.resolve("comun.sh");
    private static final Path CREAR = DIR.resolve("crear-web-acl.sh");
    private static final Path ASOCIAR = DIR.resolve("asociar-web-acl.sh");
    private static final Path PASAR = DIR.resolve("pasar-regla-a-bloqueo.sh");
    private static final Path ENSAYO = DIR.resolve("ensayo-local.sh");
    private static final List<Path> TODOS = List.of(COMUN, CREAR, ASOCIAR, PASAR, ENSAYO);

    private static String leer(Path ruta) throws IOException {
        return Files.readString(ruta);
    }

    private static String sinComentarios(String texto) {
        return texto.lines().filter(l -> !l.stripLeading().startsWith("#")).reduce("", (a, b) -> a + b + "\n");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("los scripts del WAF tienen sintaxis válida de bash")
    void sintaxisValida() throws Exception {
        for (Path ruta : TODOS) {
            Process proceso = new ProcessBuilder("bash", "-n", ruta.toString()).redirectErrorStream(true).start();
            String salida = new String(proceso.getInputStream().readAllBytes());
            assertThat(proceso.waitFor()).as("bash -n %s: %s", ruta, salida).isZero();
        }
    }

    @Test
    // spec 050 FR-011
    @DisplayName("fallan ante cualquier error y corren en simulacro salvo que se les pase su bandera de aplicar")
    void simulacroPorOmision() throws IOException {
        for (Path ruta : List.of(CREAR, ASOCIAR, PASAR, ENSAYO)) {
            assertThat(leer(ruta)).as("%s", ruta).contains("set -euo pipefail");
        }
        for (Path ruta : List.of(CREAR, ASOCIAR)) {
            String script = leer(ruta);
            assertThat(script).as("%s", ruta).contains("APLICAR=no").contains("--aplicar) APLICAR=si");
            assertThat(script).as("--simular le gana a --aplicar en %s", ruta).contains("[ \"$SIMULAR\" = si ] && APLICAR=no");
        }
    }

    @Test
    // spec 050 FR-002
    @DisplayName("pasar a Block tiene su propia bandera: --aplicar no lo hace, solo --aplicar-bloqueo (y --volver-a-count deshace una regla)")
    void elBloqueoEsUnPasoAparte() throws IOException {
        String script = leer(PASAR);
        assertThat(script).contains("--aplicar-bloqueo)").contains("--volver-a-count)");
        assertThat(script).as("--aplicar a secas no existe en este script").doesNotContain("--aplicar)");
        assertThat(script).contains("[ -n \"$REGLA\" ]").as("sin --regla no hace nada");
        assertThat(script).as("una regla por tasa bloquea con la respuesta 429 de la plantilla; un grupo, con sus acciones (None)")
                .contains("AccionDeBloqueoPorTasa").contains("'{\"None\":{}}'").contains("'{\"Count\":{}}'");
        assertThat(script).as("antes de bloquear se muestra lo que la regla contó en 7 días").contains("CountedRequests").contains("7 days ago");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("todo AWS va por aws_prod (perfil prod y región fija), la cuenta se verifica y ningún id está escrito en un repositorio público")
    void perfilCuentaYSinIds() throws IOException {
        String comun = leer(COMUN);
        assertThat(comun).contains("PERFIL=prod").contains("REGION=us-east-1").contains("ALCANCE=CLOUDFRONT");
        assertThat(comun).contains("aws_prod() { aws \"$@\" --profile \"$PERFIL\" --region \"$REGION\"; }");
        assertThat(comun).contains("${RENASER_CUENTA_AWS:?").contains("\"$CUENTA\" = \"$RENASER_CUENTA_AWS\"");
        for (Path ruta : List.of(COMUN, CREAR, ASOCIAR, PASAR)) {
            String script = leer(ruta);
            List<String> directas = sinComentarios(script).lines()
                    .filter(l -> l.matches(".*\\baws [a-z].*") && !l.contains("aws_prod() {") && !l.contains("for h in aws")
                            && !l.stripLeading().startsWith("echo")).toList();
            assertThat(directas).as("llamadas a aws sin perfil en %s", ruta).isEmpty();
            assertThat(script).as("%s", ruta).doesNotContainPattern("(?<![0-9])[0-9]{12}(?![0-9])");
            assertThat(script).as("%s", ruta).doesNotContainPattern("\\bE[0-9A-Z]{12,13}\\b").doesNotContainPattern("\\bi-[0-9a-f]{8,17}\\b");
            assertThat(script).as("%s", ruta).doesNotContainPattern("AKIA[0-9A-Z]{16}").doesNotContainPattern("\\bset -x\\b");
        }
        assertThat(leer(ASOCIAR)).contains("${RENASER_DISTRIBUCION:?");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("el simulacro de crear valida la plantilla con AWS (check-capacity) y crear/actualizar/borrar guardan un respaldo con permiso 600")
    void validaYRespalda() throws IOException {
        String crear = leer(CREAR);
        assertThat(crear).contains("check-capacity").contains("validar_con_aws");
        assertThat(crear).contains("respaldar \"web-acl-$NOMBRE-creacion\"").contains("respaldar \"web-acl-$NOMBRE-antes-de-actualizar\"")
                .contains("respaldar \"web-acl-$NOMBRE-borrado\"");
        assertThat(crear).as("crear no asocia el Web ACL: sin asociar no ve tráfico").doesNotContain("WebACLId");
        String comun = leer(COMUN);
        assertThat(comun).contains("chmod 600 \"$RESPALDO\"").contains("chmod 700").contains("RESPALDOS=${RENASER_RESPALDOS:-$HOME/.renaser/respaldos}");
        assertThat(comun).contains("umask 077");
        assertThat(leer(PASAR)).contains("respaldar \"web-acl-$NOMBRE-antes-de-$ACCION-$REGLA\"");
    }

    @Test
    // spec 050 FR-005
    @DisplayName("la asociación usa el ETag, espera a que la distribución se despliegue, no pisa otro Web ACL y se deshace quitando solo WebACLId")
    void asociacionConVueltaAtras() throws IOException {
        String asociar = leer(ASOCIAR);
        assertThat(asociar).contains("get-distribution-config").contains("--if-match \"$ETAG\"").contains("update-distribution");
        assertThat(asociar).contains("wait distribution-deployed");
        assertThat(asociar).contains("respaldar \"cloudfront-$DIST-antes-de-asociar\"").contains("respaldar \"cloudfront-$DIST-antes-de-desasociar\"");
        assertThat(asociar).contains("--reemplazar").contains("OTRO Web ACL");
        assertThat(asociar).contains(".DistributionConfig | .WebACLId = $acl").contains(".DistributionConfig | .WebACLId = \"\"");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("borrar el Web ACL se niega mientras esté asociado y deja el grupo de registros salvo que se pida")
    void borrarSoloDesasociado() throws IOException {
        String crear = leer(CREAR);
        assertThat(crear).contains("list-distributions-by-web-acl-id").contains("sigue asociado");
        assertThat(crear).contains("--borrar-registros");
        assertThat(crear.indexOf("sigue asociado")).isLessThan(crear.indexOf("delete-web-acl"));
        assertThat(crear).as("actualizar conserva el Count/Block que cada regla tenga ahora")
                .contains("$a.Action").contains("$a.OverrideAction");
    }
}
