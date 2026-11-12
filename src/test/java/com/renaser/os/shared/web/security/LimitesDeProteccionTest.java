package com.renaser.os.shared.web.security;

import com.renaser.os.shared.domain.ContadorDeVentanas.Ventana;
import com.renaser.os.shared.domain.GrupoDeLimite;
import com.renaser.os.shared.domain.ModoDeLimite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los valores de partida del límite por cuenta que trae {@code application.yaml} (spec 050, FR-013 y FR-016): los topes de la tabla
 * del plan y todos los grupos en {@code REGISTRAR}. Lee el archivo de verdad, no una copia: si alguien cambia un tope o deja un grupo
 * en {@code APLICAR} sin querer, esto falla.
 */
class LimitesDeProteccionTest {

    /** El de producción. En las pruebas, {@code src/test/resources/application.yaml} lo REEMPLAZA en el classpath, así que se lee por ruta. */
    private static LimitesDeProteccion deApplicationYaml() throws IOException {
        return desde("src/main/resources/application.yaml");
    }

    private static LimitesDeProteccion desde(String ruta) throws IOException {
        StandardEnvironment entorno = new StandardEnvironment();
        List<PropertySource<?>> fuentes = new YamlPropertySourceLoader().load("application", new FileSystemResource(ruta));
        fuentes.forEach(entorno.getPropertySources()::addLast);
        ConfigurationPropertySources.attach(entorno);
        return Binder.get(entorno).bind("renaser.proteccion.limites", LimitesDeProteccion.class).get();
    }

    private static Map<String, int[]> topesDeFr013() {
        // por minuto, por hora, por día (0 = sin tope en esa ventana)
        return Map.of(
                "lectura", new int[]{300, 0, 0},
                "escritura", new int[]{60, 0, 0},
                "muro", new int[]{10, 60, 0},
                "chat", new int[]{30, 0, 0},
                "subidas", new int[]{20, 0, 300},
                "ia-fuera-de-ser", new int[]{5, 0, 0},
                "ser", new int[]{10, 0, 0},
                "exportaciones", new int[]{5, 0, 0},
                "admin", new int[]{120, 0, 0},
                "webhook-whatsapp", new int[]{600, 0, 0});
    }

    // spec 050 FR-013
    @Test
    @DisplayName("cada grupo trae sus topes de partida (la tabla de FR-013) y no falta ninguno")
    void losTopesDeLaTabla() throws IOException {
        LimitesDeProteccion limites = deApplicationYaml();

        assertThat(GrupoDeLimite.values()).hasSameSizeAs(topesDeFr013().keySet());
        for (GrupoDeLimite grupo : GrupoDeLimite.values()) {
            int[] esperado = topesDeFr013().get(grupo.clave());
            assertThat(esperado).as("el grupo %s está en la tabla de FR-013", grupo.clave()).isNotNull();
            List<Ventana> ventanas = limites.ventanasDe(grupo);
            assertThat(ventanas.stream().filter(v -> v.nombre().equals("1m")).mapToInt(Ventana::maximo).findFirst().orElse(0))
                    .as("por minuto de %s", grupo.clave()).isEqualTo(esperado[0]);
            assertThat(ventanas.stream().filter(v -> v.nombre().equals("1h")).mapToInt(Ventana::maximo).findFirst().orElse(0))
                    .as("por hora de %s", grupo.clave()).isEqualTo(esperado[1]);
            assertThat(ventanas.stream().filter(v -> v.nombre().equals("1d")).mapToInt(Ventana::maximo).findFirst().orElse(0))
                    .as("por día de %s", grupo.clave()).isEqualTo(esperado[2]);
        }
    }

    // spec 050 FR-016
    @Test
    @DisplayName("TODOS los grupos nacen en REGISTRAR, el filtro está habilitado y el modo se relee cada 30 s")
    void todosEnRegistrar() throws IOException {
        LimitesDeProteccion limites = deApplicationYaml();

        assertThat(limites.habilitado()).isTrue();
        assertThat(limites.cacheDelModo()).isEqualTo(Duration.ofSeconds(30));
        for (GrupoDeLimite grupo : GrupoDeLimite.values()) {
            assertThat(limites.modoPorDefecto(grupo)).as("modo de partida de %s", grupo.clave()).isEqualTo(ModoDeLimite.REGISTRAR);
        }
    }

    // spec 050 FR-012
    @Test
    @DisplayName("las ventanas salen de la más corta a la más larga, con su duración: 1 m, 1 h y 24 h")
    void lasVentanas() throws IOException {
        LimitesDeProteccion limites = deApplicationYaml();

        assertThat(limites.ventanasDe(GrupoDeLimite.MURO)).containsExactly(
                new Ventana("1m", Duration.ofMinutes(1), 10), new Ventana("1h", Duration.ofHours(1), 60));
        assertThat(limites.ventanasDe(GrupoDeLimite.SUBIDAS)).containsExactly(
                new Ventana("1m", Duration.ofMinutes(1), 20), new Ventana("1d", Duration.ofHours(24), 300));
    }

    // spec 050 FR-016
    @Test
    @DisplayName("el application.yaml de las pruebas, que reemplaza al de main, dice lo mismo que el de main sobre los límites")
    void elYamlDeLasPruebasEspejaElDeMain() throws IOException {
        LimitesDeProteccion deMain = deApplicationYaml();
        LimitesDeProteccion deLasPruebas = desde("src/test/resources/application.yaml");

        assertThat(deLasPruebas.habilitado()).isEqualTo(deMain.habilitado());
        assertThat(deLasPruebas.cacheDelModo()).isEqualTo(deMain.cacheDelModo());
        for (GrupoDeLimite grupo : GrupoDeLimite.values()) {
            assertThat(deLasPruebas.ventanasDe(grupo)).as("ventanas de %s", grupo.clave()).isEqualTo(deMain.ventanasDe(grupo));
            assertThat(deLasPruebas.modoPorDefecto(grupo)).as("modo de %s", grupo.clave()).isEqualTo(deMain.modoPorDefecto(grupo));
        }
    }

    @Test
    @DisplayName("un grupo que la configuración no nombra no cuenta nada y queda en REGISTRAR")
    void unGrupoSinConfiguracion() {
        LimitesDeProteccion vacio = new LimitesDeProteccion(true, Duration.ofSeconds(30), null);

        assertThat(vacio.ventanasDe(GrupoDeLimite.MURO)).isEmpty();
        assertThat(vacio.modoPorDefecto(GrupoDeLimite.MURO)).isEqualTo(ModoDeLimite.REGISTRAR);
    }

    @Test
    @DisplayName("la clave de un grupo es la de la configuración y un nombre mal escrito no es un grupo")
    void lasClavesDeLosGrupos() {
        assertThat(GrupoDeLimite.deClave("ia-fuera-de-ser")).contains(GrupoDeLimite.IA_FUERA_DE_SER);
        assertThat(GrupoDeLimite.deClave("webhook-whatsapp")).contains(GrupoDeLimite.WEBHOOK_WHATSAPP);
        assertThat(GrupoDeLimite.deClave("muros")).isEmpty();
        assertThat(GrupoDeLimite.deClave("crm")).as("el CRM no tiene grupo propio (D15)").isEmpty();
    }
}
