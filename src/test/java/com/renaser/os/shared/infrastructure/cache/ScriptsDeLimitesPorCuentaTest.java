package com.renaser.os.shared.infrastructure.cache;

import com.renaser.os.shared.domain.GrupoDeLimite;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los dos scripts del dueño del límite por cuenta (spec 050, tramo C; FR-011 y FR-016): {@code limites-modo.sh} y
 * {@code cuentas-que-habrian-bloqueado.sh}. Se corren de verdad con un {@code aws} y un {@code docker} de mentira (sin AWS, sin red y
 * sin Redis): lo que se prueba es que el simulacro no cambia nada, que {@code --aplicar} sí, que se pueda volver atrás y que ninguno
 * deje un secreto ni use {@code redis-cli -a}.
 */
class ScriptsDeLimitesPorCuentaTest {

    private static final Path MODO = Path.of("scripts/despliegue/limites-modo.sh");
    private static final Path CUENTAS = Path.of("scripts/proteccion/cuentas-que-habrian-bloqueado.sh");

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
                  *"get-command-invocation"*"StandardOutputContent"*) printf '%%s\\n' "${SALIDA_DE_MENTIRA:-ANTERIOR=REGISTRAR}" ;;
                  *"get-command-invocation"*) echo "" ;;
                  *"ssm get-parameter"*) echo "None" ;;
                esac
                """.formatted(registroDeAws, trabajo));
        ejecutable(bin.resolve("docker"), """
                #!/usr/bin/env bash
                echo "$*" >> "%s"
                case "$*" in
                  *" GET proteccion:modo:"*) printf '%%s\\n' "${VALOR_EN_REDIS:-REGISTRAR}" ;;
                  *" HGETALL "*) printf '%%s\\n' ${HASH_EN_REDIS:-} ;;
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
        List<String> orden = new java.util.ArrayList<>(List.of("bash", script.toAbsolutePath().toString()));
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
        String json = Files.readString(trabajo.resolve("parametros.json"));
        return json;
    }

    private String registro(Path archivo) throws IOException {
        return Files.exists(archivo) ? Files.readString(archivo) : "";
    }

    // spec 050 FR-016
    @Test
    @DisplayName("la lista de grupos de los scripts es la del enum, y la clave de Redis es la que lee el backend")
    void losScriptsHablanDeLosMismosGruposQueElBackend() throws IOException {
        String esperado = Arrays.stream(GrupoDeLimite.values()).map(GrupoDeLimite::clave).collect(Collectors.joining(" "));
        for (Path script : List.of(MODO, CUENTAS)) {
            assertThat(Files.readString(script)).as(script.toString()).contains("GRUPOS=\"" + esperado + "\"");
        }
        assertThat(Files.readString(MODO)).contains(ModoDeLosLimitesRedis.PREFIJO + "$1").contains(ModoDeLosLimitesRedis.PREFIJO + "$GRUPO");
        assertThat(Files.readString(CUENTAS)).contains(CuentasQueHabrianBloqueadoRedis.PREFIJO + "$GRUPO:$DIA");
    }

    // spec 050 FR-011
    @Test
    @DisplayName("los dos scripts: set -euo pipefail, perfil prod, sin redis-cli -a, sin llaves ni contraseñas a la vista")
    void reglasDeD12() throws IOException {
        for (Path script : List.of(MODO, CUENTAS)) {
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
    @DisplayName("limites-modo.sh sin --aplicar es un simulacro: manda APLICAR=no a la instancia y no guarda ningún respaldo")
    void elSimulacroNoCambiaNada() throws Exception {
        Salida salida = correr(MODO, Map.of(), "muro", "APLICAR");

        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(salida.texto()).contains("Modo: SIMULACRO");
        assertThat(ordenEnviadaPorSsm()).contains("EN_LA_INSTANCIA=poner").contains(" APLICAR=no ").contains("GRUPO=muro")
                .contains("MODO=APLICAR");
        assertThat(Files.exists(casa.resolve(".renaser/limites-modo/muro.anterior"))).isFalse();
    }

    // spec 050 FR-011
    @Test
    @DisplayName("limites-modo.sh --aplicar manda APLICAR=si, guarda el valor anterior y dice cómo volver atrás")
    void aplicarGuardaLoAnteriorYDiceComoVolver() throws Exception {
        Salida salida = correr(MODO, Map.of("SALIDA_DE_MENTIRA", "ANTERIOR=REGISTRAR\nAPLICADO: muro=APLICAR."), "muro", "APLICAR", "--aplicar");

        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(ordenEnviadaPorSsm()).contains(" APLICAR=si ");
        assertThat(Files.readString(casa.resolve(".renaser/limites-modo/muro.anterior"))).isEqualTo("REGISTRAR");
        assertThat(salida.texto()).contains("Para volver atras").contains("--revertir muro --aplicar").doesNotContain("ANTERIOR=");
    }

    // spec 050 FR-011
    @Test
    @DisplayName("limites-modo.sh --revertir vuelve al valor guardado; sin respaldo no cambia nada")
    void revertir() throws Exception {
        Salida sinRespaldo = correr(MODO, Map.of(), "--revertir", "muro", "--aplicar");
        assertThat(sinRespaldo.codigo()).isEqualTo(1);
        assertThat(registro(registroDeAws)).as("sin respaldo no se llamó a AWS para cambiar nada").doesNotContain("send-command");

        Files.createDirectories(casa.resolve(".renaser/limites-modo"));
        Files.writeString(casa.resolve(".renaser/limites-modo/muro.anterior"), "REGISTRAR");
        Salida salida = correr(MODO, Map.of(), "--revertir", "muro", "--aplicar");

        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(ordenEnviadaPorSsm()).contains("MODO=REGISTRAR").contains(" APLICAR=si ");
    }

    // spec 050 FR-011
    @Test
    @DisplayName("limites-modo.sh rechaza un grupo o un modo que no existen, la cuenta equivocada y el simulacro por omisión no llama a la instancia con un valor suelto")
    void validaAntesDeTocarNada() throws Exception {
        assertThat(correr(MODO, Map.of(), "muros", "APLICAR").codigo()).isEqualTo(2);
        assertThat(correr(MODO, Map.of(), "muro", "BLOQUEAR").codigo()).isEqualTo(2);
        assertThat(correr(MODO, Map.of(), "muro; rm -rf /", "APLICAR").codigo()).isEqualTo(2);
        assertThat(registro(registroDeAws)).as("con datos inválidos no se llama ni a AWS").isEmpty();

        Salida otraCuenta = correr(MODO, Map.of("CUENTA_DE_MENTIRA", "999999999999"), "muro", "APLICAR", "--aplicar");
        assertThat(otraCuenta.codigo()).isEqualTo(1);
        assertThat(otraCuenta.texto()).contains("No se cambio nada");
        assertThat(registro(registroDeAws)).doesNotContain("send-command");
    }

    // spec 050 FR-016
    @Test
    @DisplayName("en la instancia: el simulacro hace solo GET; --aplicar hace SET del modo; CONFIGURACION borra la clave; el mismo modo no cambia nada")
    void loQueSeHaceEnRedis() throws Exception {
        Map<String, String> base = new HashMap<>(Map.of("EN_LA_INSTANCIA", "poner", "GRUPO", "muro", "MODO", "APLICAR", "APLICAR", "no"));

        Salida simulacro = correr(MODO, base);
        assertThat(simulacro.texto()).contains("SIMULACRO").contains("ANTERIOR=REGISTRAR");
        assertThat(registro(registroDeDocker)).contains("GET proteccion:modo:muro").doesNotContain(" SET ").doesNotContain(" DEL ");

        base.put("APLICAR", "si");
        Salida aplicado = correr(MODO, base);
        assertThat(aplicado.texto()).contains("APLICADO: muro=APLICAR");
        assertThat(registro(registroDeDocker)).contains("SET proteccion:modo:muro APLICAR");

        base.put("MODO", "CONFIGURACION");
        correr(MODO, base);
        assertThat(registro(registroDeDocker)).contains("DEL proteccion:modo:muro");

        Files.deleteIfExists(registroDeDocker);
        base.put("MODO", "REGISTRAR");
        Salida igual = correr(MODO, base);
        assertThat(igual.texto()).contains("Sin cambios");
        assertThat(registro(registroDeDocker)).doesNotContain(" SET ").doesNotContain(" DEL ");
    }

    // spec 050 FR-019
    @Test
    @DisplayName("cuentas-que-habrian-bloqueado.sh solo lee: ordena de más a menos veces y no escribe nada")
    void lasCuentasQueHabrianBloqueado() throws Exception {
        Map<String, String> entorno = Map.of("EN_LA_INSTANCIA", "leer", "GRUPO", "muro", "DIA", "2026-11-12",
                "HASH_EN_REDIS", "cuenta-a 5 cuenta-b 12 cuenta-c 1");

        Salida salida = correr(CUENTAS, entorno);

        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(salida.texto().lines().toList()).containsSubsequence("Grupo muro, dia 2026-11-12 (veces, cuenta):",
                "12\tcuenta-b", "5\tcuenta-a", "1\tcuenta-c");
        assertThat(registro(registroDeDocker)).contains("HGETALL proteccion:habria:muro:2026-11-12").doesNotContain(" SET ")
                .doesNotContain(" DEL ").doesNotContain(" HINCRBY ");
    }

    // spec 050 FR-019
    @Test
    @DisplayName("cuentas-que-habrian-bloqueado.sh pide el día como AAAA-MM-DD y manda la orden por SSM con perfil prod")
    void elDiaYLaOrden() throws Exception {
        assertThat(correr(CUENTAS, Map.of(), "muro", "ayer").codigo()).isEqualTo(2);
        assertThat(correr(CUENTAS, Map.of(), "no-existe").codigo()).isEqualTo(2);

        Salida salida = correr(CUENTAS, Map.of(), "muro", "2026-11-12");

        assertThat(salida.codigo()).as(salida.texto()).isZero();
        assertThat(ordenEnviadaPorSsm()).contains("EN_LA_INSTANCIA=leer").contains("GRUPO=muro").contains("DIA=2026-11-12");
        assertThat(registro(registroDeAws)).contains("--profile prod");
    }
}
