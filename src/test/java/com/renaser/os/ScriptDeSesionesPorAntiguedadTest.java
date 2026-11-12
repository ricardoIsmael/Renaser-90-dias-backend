package com.renaser.os;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El conteo previo del tope absoluto de sesión (spec 050 tramo E, T051): {@code scripts/proteccion/sesiones-por-antiguedad.sh}
 * es de SOLO LECTURA, cumple lo que la spec exige de todo script de infraestructura (FR-011) y cuenta bien. Se corre de
 * verdad en modo {@code --local} con un {@code docker} de mentira que responde lo que respondería Redis (las claves del
 * {@code SCAN} y el {@code creationTime} de cada {@code HGET}), con el reloj fijado.
 */
class ScriptDeSesionesPorAntiguedadTest {

    private static final Path SCRIPT = Path.of("scripts/proteccion/sesiones-por-antiguedad.sh");
    /** Donde empieza lo que corre en la máquina del dueño (con el perfil de AWS); lo anterior corre en la instancia. */
    private static final String MARCA_LOCAL = "# LO QUE CORRE EN LA MAQUINA DEL DUENO";
    private static final long AHORA = 1_790_000_000L;

    private static String sinComentarios(String texto) {
        return texto.lines().filter(l -> !l.stripLeading().startsWith("#")).reduce("", (a, b) -> a + b + "\n");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("tiene sintaxis válida de bash y falla ante cualquier error")
    void sintaxisValida() throws Exception {
        Process proceso = new ProcessBuilder("bash", "-n", SCRIPT.toString()).redirectErrorStream(true).start();
        String salida = new String(proceso.getInputStream().readAllBytes());
        assertThat(proceso.waitFor()).as("bash -n: %s", salida).isZero();
        assertThat(Files.readString(SCRIPT)).contains("set -euo pipefail");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("es de solo lectura: no escribe en Redis, no borra y no toca Parameter Store")
    void soloLectura() throws IOException {
        String codigo = sinComentarios(Files.readString(SCRIPT));
        assertThat(codigo).doesNotContainPattern("\\b(DEL|UNLINK|HSET|HDEL|SET|SETEX|EXPIRE|FLUSHDB|FLUSHALL|SADD|SREM|CONFIG)\\s+[\"'$a-z]")
                .doesNotContain("put-parameter").doesNotContain("delete-parameter").doesNotContain("--aplicar");
        assertThat(codigo).contains("--scan").contains("HGET");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("declara el perfil prod en cada llamada a AWS de la máquina del dueño y verifica la cuenta sin escribirla")
    void perfilYCuentaVerificada() throws IOException {
        String script = Files.readString(SCRIPT);
        assertThat(script).contains("PERFIL=prod");
        String local = script.substring(script.indexOf(MARCA_LOCAL));
        List<String> llamadas = sinComentarios(local).lines()
                .filter(l -> !l.stripLeading().startsWith("echo") && Pattern.compile("\\baws (ssm|sts)\\b").matcher(l).find()).toList();
        assertThat(llamadas).isNotEmpty();
        assertThat(llamadas).allSatisfy(l -> assertThat(l).as("llamada a AWS sin perfil: %s", l).contains("--profile \"$PERFIL\""));
        assertThat(script).contains("${RENASER_CUENTA_AWS:?").contains("\"$CUENTA\" != \"$RENASER_CUENTA_AWS\"");
        assertThat(script).doesNotContainPattern("(?<![0-9])[0-9]{12}(?![0-9])");
        assertThat(script).doesNotContainPattern("\\bi-[0-9a-f]{8,17}\\b");
    }

    @Test
    // spec 050 FR-011
    @DisplayName("la clave de Redis no viaja por argumentos ni se imprime: REDISCLI_AUTH y Parameter Store")
    void sinSecretos() throws IOException {
        String codigo = sinComentarios(Files.readString(SCRIPT));
        assertThat(codigo).doesNotContainPattern("redis-cli[^\\n]*\\s(-a|--pass)\\b");
        assertThat(codigo).contains("REDISCLI_AUTH").doesNotContainPattern("\\bset -x\\b");
        assertThat(codigo).doesNotContainPattern("echo[^\\n]*clave_de_redis").doesNotContainPattern("printf[^\\n]*clave_de_redis");
    }

    /** Un {@code docker} de mentira: el {@code SCAN} imprime las claves del fixture que calzan con el patrón y cada {@code HGET} su valor. */
    private static Path dockerDeMentira(Path carpeta, Map<String, String> sesiones) throws IOException {
        List<String> claves = new ArrayList<>(sesiones.keySet());
        Files.write(carpeta.resolve("claves.txt"), claves);
        List<String> valores = new ArrayList<>();
        sesiones.forEach((clave, valor) -> valores.add(clave + " " + valor));
        Files.write(carpeta.resolve("valores.txt"), valores);
        Path docker = carpeta.resolve("docker");
        Files.writeString(docker, """
                #!/usr/bin/env bash
                if printf '%s\\n' "$@" | grep -qx -- '--scan'; then
                  patron=""; previo=""
                  for a in "$@"; do [ "$previo" = "--pattern" ] && patron=$a; previo=$a; done
                  while read -r c; do [[ $c == $patron ]] && echo "$c"; done < "$FIXTURE/claves.txt"
                  exit 0
                fi
                while read -r orden clave campo; do
                  [ "$orden" = HGET ] || { echo "orden inesperada: $orden" >&2; exit 9; }
                  grep -F -- "$clave " "$FIXTURE/valores.txt" | head -1 | cut -d' ' -f2-
                done
                """);
        Files.setPosixFilePermissions(docker, PosixFilePermissions.fromString("rwxr-xr-x"));
        return carpeta;
    }

    private static String creada(long diasAtras) {
        return "[\"java.lang.Long\"," + (AHORA - diasAtras * 86_400L) * 1000 + "]";
    }

    private static String correr(Path carpeta, String... argumentos) throws Exception {
        List<String> orden = new ArrayList<>(List.of("bash", SCRIPT.toString(), "--local"));
        orden.addAll(List.of(argumentos));
        ProcessBuilder proceso = new ProcessBuilder(orden).redirectErrorStream(true);
        proceso.environment().put("PATH", carpeta + ":" + System.getenv("PATH"));
        proceso.environment().put("FIXTURE", carpeta.toString());
        proceso.environment().put("AHORA_EPOCH", String.valueOf(AHORA));
        proceso.environment().remove("REDISCLI_AUTH");
        Process p = proceso.start();
        String salida = new String(p.getInputStream().readAllBytes());
        assertThat(p.waitFor(30, TimeUnit.SECONDS)).as("el script no terminó: %s", salida).isTrue();
        assertThat(p.exitValue()).as(salida).isZero();
        return salida;
    }

    @Test
    @DisplayName("cuenta las sesiones que ya pasaron el tope (el día uno) y las que lo cumplen en 7, 14, 30 y 60 días")
    void cuentaPorAntiguedad(@TempDir Path carpeta) throws Exception {
        Map<String, String> sesiones = new java.util.LinkedHashMap<>();
        long[] edades = {120, 100, 91, 89, 85, 80, 70, 40, 20, 1};
        for (int i = 0; i < edades.length; i++) {
            sesiones.put("renaser:session:v2:sessions:s" + i, creada(edades[i]));
        }
        // El aviso de expiración de una sesión NO es una sesión: no se cuenta.
        sesiones.put("renaser:session:v2:sessions:expires:s0", "");
        dockerDeMentira(carpeta, sesiones);

        String salida = correr(carpeta);

        assertThat(salida).contains("10 sesiones.").contains("La mas vieja tiene 120 dias");
        assertThat(salida).containsPattern("\\s3\\s+ya lo pasaron: se cortarian el DIA UNO");
        assertThat(salida).containsPattern("\\s2\\s+lo cumplen en los proximos 7 dias");
        assertThat(salida).containsPattern("\\s1\\s+lo cumplen en 7 a 14 dias");
        assertThat(salida).containsPattern("\\s1\\s+lo cumplen en 14 a 30 dias");
        assertThat(salida).containsPattern("\\s1\\s+lo cumplen en 30 a 60 dias");
        assertThat(salida).containsPattern("\\s2\\s+lo cumplen dentro de mas de 60 dias");
    }

    @Test
    @DisplayName("con otro tope (--tope-dias 60) la cuenta cambia, y con otro espacio de nombres no ve las sesiones de otro")
    void otroTopeYOtroEspacio(@TempDir Path carpeta) throws Exception {
        Map<String, String> sesiones = new java.util.LinkedHashMap<>();
        sesiones.put("renaser:session:v2:sessions:a", creada(70));
        sesiones.put("renaser:session:v2:sessions:b", creada(10));
        dockerDeMentira(carpeta, sesiones);

        String con60 = correr(carpeta, "--tope-dias", "60");

        assertThat(con60).containsPattern("\\s1\\s+ya lo pasaron");
        assertThat(correr(carpeta)).containsPattern("\\s0\\s+ya lo pasaron");
        assertThat(correr(carpeta, "--namespace", "otro:espacio")).contains("0 sesiones").contains("No hay sesiones");
    }

    @Test
    @DisplayName("rechaza un tope que no es un número de días y un espacio de nombres con caracteres raros")
    void argumentosInvalidos(@TempDir Path carpeta) throws Exception {
        for (String[] argumentos : new String[][] {{"--tope-dias", "noventa"}, {"--tope-dias", "0"},
                {"--namespace", "a;rm -rf /"}, {"--contenedor", "x y"}, {"--cosa"}}) {
            List<String> orden = new ArrayList<>(List.of("bash", SCRIPT.toString(), "--local"));
            orden.addAll(List.of(argumentos));
            Process p = new ProcessBuilder(orden).redirectErrorStream(true).start();
            String salida = new String(p.getInputStream().readAllBytes());
            assertThat(p.waitFor()).as("%s: %s", String.join(" ", argumentos), salida).isEqualTo(2);
        }
    }
}
