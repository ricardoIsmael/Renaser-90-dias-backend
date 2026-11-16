package com.renaser.os;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El script y la plantilla de Redis con contraseña (spec 050 tramo A1) cumplen lo que la spec exige de todo script
 * de infraestructura (FR-011) y lo que pide FR-010. Es una prueba estática: el recurso real vive en la cuenta de AWS
 * del dueño y lo ensaya {@code infra/redis/ensayo-local.sh} contra un Redis de juguete en Docker.
 */
class ScriptDeRedisConContrasenaTest {

    private static final Path SCRIPT = Path.of("infra/redis/contrasena.sh");
    private static final Path ENSAYO = Path.of("infra/redis/ensayo-local.sh");
    private static final Path PLANTILLA = Path.of("infra/redis/redis.conf.plantilla");
    /** Donde empieza lo que corre en la máquina del dueño (con el perfil de AWS); lo anterior corre en la instancia. */
    private static final String MARCA_LOCAL = "# LO QUE CORRE EN LA MAQUINA DEL DUENO";

    private static String leer(Path ruta) throws IOException {
        return Files.readString(ruta);
    }

    private static String sinComentarios(String texto) {
        return texto.lines().filter(l -> !l.stripLeading().startsWith("#")).reduce("", (a, b) -> a + b + "\n");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("los scripts de Redis tienen sintaxis válida de bash")
    void sintaxisValida() throws Exception {
        for (Path ruta : List.of(SCRIPT, ENSAYO)) {
            Process proceso = new ProcessBuilder("bash", "-n", ruta.toString()).redirectErrorStream(true).start();
            String salida = new String(proceso.getInputStream().readAllBytes());
            assertThat(proceso.waitFor()).as("bash -n %s: %s", ruta, salida).isZero();
        }
    }

    @Test
    // spec 050 FR-011
    @DisplayName("falla ante cualquier error, y corre en simulacro salvo que se le pase --aplicar")
    void simulacroPorOmision() throws IOException {
        String script = leer(SCRIPT);
        assertThat(script).contains("set -euo pipefail");
        assertThat(script).contains("APLICAR=${APLICAR:-no}");
        assertThat(script).contains("--aplicar) APLICAR=si");
        assertThat(script).as("--simular tiene que ganarle a --aplicar").contains("[ \"$SIMULAR\" = si ] && APLICAR=no");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("declara el perfil prod en cada llamada a AWS de la máquina del dueño y verifica la cuenta sin escribirla")
    void perfilYCuentaVerificada() throws IOException {
        String script = leer(SCRIPT);
        assertThat(script).contains("PERFIL=prod");
        String local = script.substring(script.indexOf(MARCA_LOCAL));
        List<String> llamadas = sinComentarios(local).lines()
                .filter(l -> !l.stripLeading().startsWith("echo") && Pattern.compile("\\baws (ssm|sts)\\b").matcher(l).find()).toList();
        assertThat(llamadas).isNotEmpty();
        assertThat(llamadas).allSatisfy(l -> assertThat(l).as("llamada a AWS sin perfil: %s", l).contains("--profile \"$PERFIL\""));
        assertThat(script).contains("${RENASER_CUENTA_AWS:?");
        assertThat(script).contains("\"$CUENTA\" != \"$RENASER_CUENTA_AWS\"");
        assertThat(script).as("el id de la cuenta no se escribe en un repositorio público")
                .doesNotContainPattern("(?<![0-9])[0-9]{12}(?![0-9])");
        assertThat(script).doesNotContainPattern("\\bi-[0-9a-f]{8,17}\\b");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("la clave no viaja por argumentos ni se imprime: REDISCLI_AUTH, stdin y Parameter Store")
    void sinSecretosEnPantallaNiEnElRepositorio() throws IOException {
        String script = sinComentarios(leer(SCRIPT));
        assertThat(script).as("redis-cli con -a deja la clave en la lista de procesos").doesNotContainPattern("redis-cli[^\\n]*\\s(-a|--pass)\\b");
        assertThat(script).contains("REDISCLI_AUTH");
        assertThat(script).as("jq --arg dejaria la clave en la linea de comandos: va con --rawfile").contains("--rawfile");
        assertThat(script).doesNotContainPattern("\\bset -x\\b");
        for (Path ruta : List.of(SCRIPT, ENSAYO, PLANTILLA)) {
            String texto = leer(ruta);
            assertThat(texto).as("%s no lleva ninguna cadena larga en hexadecimal", ruta).doesNotContainPattern("\\b[0-9a-fA-F]{32,}\\b");
            assertThat(texto).doesNotContainPattern("AKIA[0-9A-Z]{16}");
        }
    }

    @Test
    // spec 050 FR-010
    @DisplayName("la clave vive en Parameter Store como SecureString y el usuario es «default»")
    void parametros() throws IOException {
        String script = leer(SCRIPT);
        assertThat(script).contains("REDIS_PASSWORD").contains("SecureString").contains("REDIS_USERNAME");
        assertThat(script).contains("Overwrite: false");
        assertThat(script).contains("--value default");
    }

    @Test
    // spec 050 FR-010
    @DisplayName("la plantilla exige contraseña (con marcador), guarda los datos y deja a Spring Session sus eventos")
    void plantilla() throws IOException {
        String conf = leer(PLANTILLA);
        List<String> activas = conf.lines().filter(l -> !l.isBlank() && !l.startsWith("#")).toList();
        assertThat(activas).contains("requirepass __CLAVE__", "appendonly yes", "notify-keyspace-events Egx", "dir /data");
        assertThat(activas.stream().filter(l -> l.startsWith("requirepass")).count()).isEqualTo(1);
    }

    @Test
    // spec 050 FR-010
    @DisplayName("el Redis nuevo no publica ningún puerto, y el redis.conf queda con permiso 600")
    void sinPuertosYConfPrivada() throws IOException {
        String script = leer(SCRIPT);
        int desde = script.indexOf("arrancar_redis() {");
        String cuerpo = script.substring(desde, script.indexOf("\n}\n", desde));
        assertThat(cuerpo).contains("docker run").doesNotContain(" -p ").doesNotContain("--publish");
        assertThat(script).contains("umask 077").contains("chmod 600");
        assertThat(script).contains("NOAUTH");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("imprime y prueba la vuelta atrás: el Redis viejo queda parado y hay --revertir")
    void vueltaAtras() throws IOException {
        String script = leer(SCRIPT);
        assertThat(script).contains("--revertir").contains("PREVIO=").contains("respaldo/previo.json");
        assertThat(script).contains("Vuelta atras ahora:");
        assertThat(leer(ENSAYO)).contains("== 4. Vuelta atras ==");
    }

    @Test
    @DisplayName("La limpieza al salir fija la ruta al crearla: una variable local ya no existe en el EXIT y con set -u el script termina en error")
    void laLimpiezaAlSalirNoUsaVariablesQueYaNoExisten() throws IOException {
        // Un trap con comillas simples y $variable se expande AL SALIR, cuando la variable local de la funcion ya no existe
        // ("tmp: variable sin asignar" al terminar el paso parametros, 2026-11-11). Hay que expandirla al definir el trap.
        assertThat(Pattern.compile("trap '[^']*\\$[^']*' EXIT").matcher(leer(SCRIPT)).find())
                .as("usa trap \"rm -rf '$ruta'\" EXIT (comillas dobles afuera) para que la ruta quede fija al crearla")
                .isFalse();
    }
}
