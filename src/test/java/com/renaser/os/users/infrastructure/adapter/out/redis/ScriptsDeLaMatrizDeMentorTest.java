package com.renaser.os.users.infrastructure.adapter.out.redis;

import com.renaser.os.users.application.ports.out.matriz.ModoDeLaMatriz;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los dos scripts del dueño de la matriz de Mentor y Guía (spec 050, tramo H; FR-011 y FR-041): {@code permisos-modo.sh} y
 * {@code denegaciones-de-la-matriz.sh}. Se corren de verdad con un {@code aws} y un {@code docker} de mentira (sin AWS, sin red y sin
 * Redis): lo que se prueba es que el simulacro no cambia nada, que {@code --aplicar} sí, que se pueda volver atrás, que hablen de la
 * misma clave de Redis que lee el backend y que ninguno deje un secreto ni use {@code redis-cli -a}.
 */
class ScriptsDeLaMatrizDeMentorTest {

    private static final Path MODO = Path.of("scripts/despliegue/permisos-modo.sh");
    private static final Path DENEGACIONES = Path.of("scripts/proteccion/denegaciones-de-la-matriz.sh");

    @TempDir
    Path trabajo;

    private Path bin;
    private Path casa;
    private Path registroDeAws;
    private Path registroDeDocker;

    @BeforeEach
    void armarElEntornoDeMentira() throws IOException {
        bin = Files.createDirectories(trabajo.resolve("bin"));
        casa = Files.createDirectories(trabajo.resolve("casa"));
        registroDeAws = trabajo.resolve("aws.log");
        registroDeDocker = trabajo.resolve("docker.log");
        ejecutable(bin.resolve("aws"), """
                #!/usr/bin/env bash
                echo "$*" >> "%s"
                case "$*" in
                  *"sts get-caller-identity"*) echo "${CUENTA_DE_MENTIRA:-123456789012}" ;;
                  *"ssm send-command"*)
                    for a in "$@"; do case "$a" in file://*) cp "${a#file://}" "%s/parametros.json" ;; esac; done
                    echo "comando-1" ;;
                  *"get-command-invocation"*"--query Status"*) echo "Success" ;;
                  *"get-command-invocation"*"StandardOutputContent"*) printf '%%s\\n' "${SALIDA_DE_MENTIRA:-ANTERIOR=SOMBRA}" ;;
                  *"get-command-invocation"*) echo "" ;;
                  *"ssm get-parameter"*) echo "None" ;;
                esac
                """.formatted(registroDeAws, trabajo));
        ejecutable(bin.resolve("docker"), """
                #!/usr/bin/env bash
                echo "$*" >> "%s"
                case "$*" in
                  *" GET proteccion:permisos:modo"*) printf '%%s\\n' "${VALOR_EN_REDIS:-SOMBRA}" ;;
                  *" HGETALL "*) printf '%%s\\n' "${HASH_EN_REDIS:-}" ;;
                esac
                """.formatted(registroDeDocker));
    }

    private static void ejecutable(Path archivo, String contenido) throws IOException {
        Files.writeString(archivo, contenido, StandardCharsets.UTF_8);
        Files.setPosixFilePermissions(archivo, PosixFilePermissions.fromString("rwxr-xr-x"));
    }

    private record Salida(int codigo, String texto) {
    }

    private Salida correr(Path script, Map<String, String> entorno, String... argumentos) throws Exception {
        List<String> orden = new ArrayList<>(List.of("bash", script.toAbsolutePath().toString()));
        orden.addAll(Arrays.asList(argumentos));
        ProcessBuilder proceso = new ProcessBuilder(orden).redirectErrorStream(true);
        Map<String, String> env = proceso.environment();
        env.put("PATH", bin + ":" + env.get("PATH"));
        env.put("HOME", casa.toString());
        env.put("RENASER_CUENTA_AWS", "123456789012");
        env.put("RENASER_INSTANCIA", "i-0abc123");
        env.putAll(entorno);
        Process p = proceso.start();
        String texto = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor(60, TimeUnit.SECONDS)).as("el script no terminó: " + texto).isTrue();
        return new Salida(p.exitValue(), texto);
    }

    private String ordenEnviadaPorSsm() throws IOException {
        return Files.readString(trabajo.resolve("parametros.json"));
    }

    private String registro(Path archivo) throws IOException {
        return Files.exists(archivo) ? Files.readString(archivo) : "";
    }

    // spec 050 FR-041
    @Test
    @DisplayName("los scripts hablan de la misma clave de Redis que lee y escribe el backend, y de los mismos modos")
    void losScriptsHablanDeLoMismoQueElBackend() throws IOException {
        String modos = Arrays.stream(ModoDeLaMatriz.values()).map(Enum::name).collect(Collectors.joining(" "));

        assertThat(Files.readString(MODO)).contains("CLAVE=\"" + ModoDeLaMatrizRedis.CLAVE + "\"")
                .contains("MODOS=\"" + modos + " CONFIGURACION\"");
        assertThat(Files.readString(DENEGACIONES)).contains("CLAVES=\"" + DenegacionesDeLaMatrizRedis.PREFIJO + "\"");
    }

    // spec 050 FR-011
    @Test
    @DisplayName("los dos scripts: set -euo pipefail, perfil prod, sin redis-cli -a, sin llaves ni contraseñas a la vista")
    void reglasDeD12() throws IOException {
        for (Path script : List.of(MODO, DENEGACIONES)) {
            String texto = Files.readString(script);
            assertThat(texto).as(script.toString()).contains("set -euo pipefail").contains("PERFIL=prod")
                    .contains("sts get-caller-identity").contains("REDISCLI_AUTH");
            assertThat(texto).doesNotContainPattern("redis-cli\\s+-a\\b").doesNotContainPattern("AKIA[0-9A-Z]{16}")
                    .doesNotContainPattern("(?i)password\\s*=\\s*['\"][^'\"$]");
            assertThat(Files.isExecutable(script)).as("%s es ejecutable", script).isTrue();
        }
    }

    // spec 050 FR-011
    @Test
    @DisplayName("permisos-modo.sh sin --aplicar es un simulacro: manda APLICAR=no a la instancia y no guarda ningún respaldo")
    void elSimulacroNoCambiaNada() throws Exception {
        Salida salida = correr(MODO, Map.of(), "APLICAR");

        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(salida.texto()).contains("Modo: SIMULACRO");
        assertThat(ordenEnviadaPorSsm()).contains("EN_LA_INSTANCIA=poner").contains(" APLICAR=no ").contains("MODO=APLICAR");
        assertThat(Files.exists(casa.resolve(".renaser/permisos-modo/anterior"))).isFalse();
    }

    // spec 050 FR-011
    @Test
    @DisplayName("permisos-modo.sh --aplicar manda APLICAR=si, guarda el valor anterior y dice cómo volver atrás")
    void aplicarGuardaLoAnteriorYDiceComoVolver() throws Exception {
        Salida salida = correr(MODO, Map.of("SALIDA_DE_MENTIRA", "ANTERIOR=SOMBRA\nAPLICADO: la matriz de Mentor y Guia esta en APLICAR."),
                "APLICAR", "--aplicar");

        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(ordenEnviadaPorSsm()).contains(" APLICAR=si ");
        assertThat(Files.readString(casa.resolve(".renaser/permisos-modo/anterior"))).isEqualTo("SOMBRA");
        assertThat(salida.texto()).contains("Para volver atras").contains("--revertir --aplicar").contains("SOMBRA --aplicar")
                .doesNotContain("ANTERIOR=");
    }

    // spec 050 FR-011
    @Test
    @DisplayName("permisos-modo.sh --revertir vuelve al valor guardado; sin respaldo no cambia nada")
    void revertir() throws Exception {
        Salida sinRespaldo = correr(MODO, Map.of(), "--revertir", "--aplicar");
        assertThat(sinRespaldo.codigo()).isEqualTo(1);
        assertThat(registro(registroDeAws)).as("sin respaldo no se llamó a AWS para cambiar nada").doesNotContain("send-command");

        Files.createDirectories(casa.resolve(".renaser/permisos-modo"));
        Files.writeString(casa.resolve(".renaser/permisos-modo/anterior"), "SOMBRA");
        Salida salida = correr(MODO, Map.of(), "--revertir", "--aplicar");

        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(ordenEnviadaPorSsm()).contains("MODO=SOMBRA").contains(" APLICAR=si ");
    }

    // spec 050 FR-011
    @Test
    @DisplayName("permisos-modo.sh rechaza un modo que no existe, la cuenta equivocada, y no llama a AWS con datos inválidos")
    void validaAntesDeTocarNada() throws Exception {
        assertThat(correr(MODO, Map.of(), "BLOQUEAR").codigo()).isEqualTo(2);
        assertThat(correr(MODO, Map.of(), "APLICAR; rm -rf /").codigo()).isEqualTo(2);
        assertThat(correr(MODO, Map.of()).codigo()).as("sin modo").isEqualTo(2);
        assertThat(registro(registroDeAws)).as("con datos inválidos no se llama ni a AWS").isEmpty();

        Salida otraCuenta = correr(MODO, Map.of("CUENTA_DE_MENTIRA", "999999999999"), "APLICAR", "--aplicar");
        assertThat(otraCuenta.codigo()).isEqualTo(1);
        assertThat(otraCuenta.texto()).contains("No se cambio nada");
        assertThat(registro(registroDeAws)).doesNotContain("send-command");
    }

    // spec 050 FR-041
    @Test
    @DisplayName("en la instancia: el simulacro hace solo GET; --aplicar hace SET; CONFIGURACION borra la clave; el mismo modo no cambia nada")
    void loQueSeHaceEnRedis() throws Exception {
        Map<String, String> base = new HashMap<>(Map.of("EN_LA_INSTANCIA", "poner", "MODO", "APLICAR", "APLICAR", "no"));

        Salida simulacro = correr(MODO, base);
        assertThat(simulacro.texto()).contains("SIMULACRO").contains("ANTERIOR=SOMBRA");
        assertThat(registro(registroDeDocker)).contains("GET proteccion:permisos:modo").doesNotContain(" SET ").doesNotContain(" DEL ");

        base.put("APLICAR", "si");
        Salida aplicado = correr(MODO, base);
        assertThat(aplicado.texto()).contains("APLICADO: la matriz de Mentor y Guia esta en APLICAR");
        assertThat(registro(registroDeDocker)).contains("SET proteccion:permisos:modo APLICAR");

        base.put("MODO", "CONFIGURACION");
        correr(MODO, base);
        assertThat(registro(registroDeDocker)).contains("DEL proteccion:permisos:modo");

        Files.deleteIfExists(registroDeDocker);
        base.put("MODO", "SOMBRA");
        Salida igual = correr(MODO, base);
        assertThat(igual.texto()).contains("Sin cambios");
        assertThat(registro(registroDeDocker)).doesNotContain(" SET ").doesNotContain(" DEL ");
    }

    // spec 050 FR-041
    @Test
    @DisplayName("permisos-modo.sh estado solo lee: dice qué hay en Redis y no escribe nada")
    void elEstadoSoloLee() throws Exception {
        Salida salida = correr(MODO, Map.of("EN_LA_INSTANCIA", "estado", "VALOR_EN_REDIS", "APLICAR"));

        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(salida.texto()).contains("Modo fijado en Redis: APLICAR");
        assertThat(registro(registroDeDocker)).doesNotContain(" SET ").doesNotContain(" DEL ");
    }

    // spec 050 FR-041
    @Test
    @DisplayName("denegaciones-de-la-matriz.sh solo lee: suma por endpoint, ordena de más a menos veces y no escribe nada")
    void lasDenegaciones() throws Exception {
        String hash = String.join("\n",
                "HABRIA_DENEGADO MENTOR VIEW_MENTOR_CORPS GET /api/v1/leadership/mentors", "5",
                "HABRIA_DENEGADO TRAINEE_GUIDE MANAGE_EVIDENCE GET /api/v1/admin/evidence/{id}", "12",
                "DENEGADO MENTOR MODERATE_WALL GET /api/v1/wall/hidden", "1");

        Salida salida = correr(DENEGACIONES, Map.of("EN_LA_INSTANCIA", "leer", "DIAS", "1", "HASH_EN_REDIS", hash));

        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(salida.texto().lines().toList()).containsSubsequence(
                "Ultimos 1 dias (veces, estado, rol, permiso, metodo, ruta):",
                "12\tHABRIA_DENEGADO TRAINEE_GUIDE MANAGE_EVIDENCE GET /api/v1/admin/evidence/{id}",
                "5\tHABRIA_DENEGADO MENTOR VIEW_MENTOR_CORPS GET /api/v1/leadership/mentors",
                "1\tDENEGADO MENTOR MODERATE_WALL GET /api/v1/wall/hidden",
                "Total por permiso (veces, estado, permiso):",
                "12\tHABRIA_DENEGADO MANAGE_EVIDENCE");
        assertThat(registro(registroDeDocker)).contains("HGETALL proteccion:permisos:denegaciones:").doesNotContain(" SET ")
                .doesNotContain(" DEL ").doesNotContain(" HINCRBY ");
    }

    // spec 050 FR-041
    @Test
    @DisplayName("denegaciones-de-la-matriz.sh lee un hash por día (hoy y los anteriores, hora de Lima) y pide los días como un número de 1 a 30")
    void losDiasYLaOrden() throws Exception {
        assertThat(correr(DENEGACIONES, Map.of(), "ayer").codigo()).isEqualTo(2);
        assertThat(correr(DENEGACIONES, Map.of(), "0").codigo()).isEqualTo(2);
        assertThat(correr(DENEGACIONES, Map.of(), "31").codigo()).isEqualTo(2);
        assertThat(registro(registroDeAws)).as("con días inválidos no se llama a AWS").isEmpty();

        Salida enLaInstancia = correr(DENEGACIONES, Map.of("EN_LA_INSTANCIA", "leer", "DIAS", "3"));
        assertThat(enLaInstancia.codigo()).as(enLaInstancia.texto()).isZero();
        long hashesLeidos = registro(registroDeDocker).lines().filter(l -> l.contains("HGETALL proteccion:permisos:denegaciones:")).distinct().count();
        assertThat(hashesLeidos).as("un hash distinto por cada uno de los 3 días").isEqualTo(3);

        Salida salida = correr(DENEGACIONES, Map.of(), "3");
        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(ordenEnviadaPorSsm()).contains("EN_LA_INSTANCIA=leer").contains("DIAS=3");
        assertThat(registro(registroDeAws)).contains("--profile prod");
    }
}
