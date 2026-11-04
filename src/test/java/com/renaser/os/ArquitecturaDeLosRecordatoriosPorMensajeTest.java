package com.renaser.os;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Donde vive cada pieza de los recordatorios por mensaje (spec 041, FR-006 y FR-033): el envio, el webhook y la tabla en {@code notifications}; {@code calendar}
 * solo decide QUE y CUANDO; {@code chat} solo suma un caso de uso en su {@code api}; ningun modulo lee tablas ajenas; y ninguna clase pasa el techo de lineas.
 */
class ArquitecturaDeLosRecordatoriosPorMensajeTest {

    private static final JavaClasses CODIGO = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.renaser.os");
    private static final Path FUENTES = Path.of("src/main/java/com/renaser/os");
    /** El techo de la regla 01: clase <= 150 lineas, techo duro 300. */
    private static final int TECHO_DE_LINEAS = 300;

    // spec 041 FR-006
    @Test
    @DisplayName("chat no depende de notifications: la llamada va de notifications hacia chat.api")
    void chatNoConoceANotifications() {
        noClasses().that().resideInAPackage("com.renaser.os.chat..").should().dependOnClassesThat().resideInAPackage("com.renaser.os.notifications..")
                .check(CODIGO);
    }

    // spec 041 FR-033
    @Test
    @DisplayName("calendar solo decide QUE y CUANDO: no conoce a notifications ni al proveedor de WhatsApp")
    void calendarNoConoceElCanal() {
        noClasses().that().resideInAPackage("com.renaser.os.calendar..").should().dependOnClassesThat().resideInAnyPackage(
                "com.renaser.os.notifications..", "..whatsapp..").check(CODIGO);
    }

    // spec 041 FR-018, FR-033
    @Test
    @DisplayName("el resto del sistema no sabe de YCloud: ninguna clase fuera de su adaptador lo nombra")
    void soloElAdaptadorSabeDeYCloud() throws IOException {
        List<Path> fuera = archivos().filter(p -> !p.toString().contains("/adapter/out/whatsapp/") && !p.toString().endsWith("/config/WhatsAppProperties.java"))
                .filter(p -> contiene(p, "api.ycloud.com")
                || contiene(p, "sendDirectly")).toList();

        assertThat(fuera).as("solo el adaptador (y la direccion por defecto de su configuracion) nombran la API de YCloud").isEmpty();
    }

    // spec 041 FR-033
    @Test
    @DisplayName("la tabla entregas_whatsapp solo la toca notifications: ningun otro modulo la lee ni la escribe")
    void ningunModuloLeeTablasAjenas() throws IOException {
        List<Path> fuera = archivos().filter(p -> !p.toString().contains("/notifications/")).filter(p -> contiene(p, "entregas_whatsapp")).toList();

        assertThat(fuera).isEmpty();
    }

    // spec 041 FR-033
    @Test
    @DisplayName("el envio, el webhook y la tabla estan en notifications")
    void todoEstaEnNotifications() throws IOException {
        List<Path> dondeEstan = archivos().filter(p -> contiene(p, "entregas_whatsapp") || contiene(p, "YCloud-Signature")).toList();

        assertThat(dondeEstan).isNotEmpty().allMatch(p -> p.toString().contains("/notifications/"));
    }

    // spec 041 FR-033
    @Test
    @DisplayName("ninguna clase nueva pasa el techo de lineas de la regla 01")
    void ningunaClasePasaElTecho() throws IOException {
        List<String> pasadas = archivos().filter(p -> p.toString().contains("whatsapp") || p.toString().contains("MensajeDeEvento")
                        || p.toString().contains("RecordatorioDeEvento") || p.toString().contains("PlanDeMensajesDelEvento")
                        || p.toString().contains("DestinatarioDelMensajeDeEvento") || p.toString().contains("ReglasDelMensajeDeEvento")
                        || p.toString().contains("AvisoDeFallaDeWhatsApp"))
                .filter(p -> lineas(p) > TECHO_DE_LINEAS).map(Path::toString).toList();

        assertThat(pasadas).isEmpty();
    }

    private static Stream<Path> archivos() throws IOException {
        return Files.walk(FUENTES).filter(p -> p.toString().endsWith(".java"));
    }

    private static boolean contiene(Path archivo, String texto) {
        try {
            return Files.readString(archivo).contains(texto);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long lineas(Path archivo) {
        try (Stream<String> lineas = Files.lines(archivo)) {
            return lineas.count();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
