package com.renaser.os.plataforma;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Issue #61: la V160 no fijaba {@code SET search_path TO renaser, public;} y en una base cuyo {@code search_path} por defecto es
 * {@code public} fallo con {@code relation "habitos" does not exist}. Las migraciones ya aplicadas no se tocan (Flyway compara su
 * checksum); desde la V194 toda migracion lo fija en sus primeras lineas, asi no depende de como este configurada la base.
 */
class MigracionesFijanElEsquemaTest {

    private static final Path MIGRACIONES = Path.of("src/main/resources/db/migration");
    /**
     * La ultima migracion aplicada en produccion sin fijar el esquema. La regla nacio el 2026-11-09 con 187, pero se fusiono el
     * 2026-11-16, cuando la V193 ({@code tipo_de_evento_sesion_personalizada}) ya estaba aplicada en produccion sin el
     * {@code SET search_path}: no se puede editar (Flyway compara su checksum), asi que la regla exige desde la V194. Las V188 a
     * V192 y V194 a V196 ya lo fijaban.
     */
    private static final int ULTIMA_ANTERIOR_A_LA_REGLA = 193;
    private static final Pattern VERSION = Pattern.compile("^V(\\d+)__.*\\.sql$");
    private static final Pattern FIJA_EL_ESQUEMA = Pattern.compile("(?im)^\\s*SET\\s+search_path\\s+TO\\s+renaser\\b");

    @Test
    @DisplayName("Toda migracion nueva fija SET search_path TO renaser (issue #61)")
    void lasNuevasFijanElEsquema() throws IOException {
        List<String> sinEsquema;
        try (Stream<Path> archivos = Files.list(MIGRACIONES)) {
            sinEsquema = archivos
                    .filter(archivo -> version(archivo) > ULTIMA_ANTERIOR_A_LA_REGLA)
                    .filter(archivo -> !FIJA_EL_ESQUEMA.matcher(leer(archivo)).find())
                    .map(archivo -> archivo.getFileName().toString())
                    .sorted()
                    .toList();
        }
        assertThat(sinEsquema)
                .as("Estas migraciones no fijan el esquema. Agrega al principio, despues de los comentarios: "
                        + "SET search_path TO renaser, public;  (sin eso migran sobre el esquema por defecto de la base, issue #61)")
                .isEmpty();
    }

    @Test
    @DisplayName("La regla detecta una migracion sin SET search_path")
    void laReglaDetecta() {
        assertThat(FIJA_EL_ESQUEMA.matcher("-- comentario\nUPDATE habitos SET x = 1;").find()).isFalse();
        assertThat(FIJA_EL_ESQUEMA.matcher("-- comentario\nSET search_path TO renaser, public;\nUPDATE habitos SET x = 1;").find()).isTrue();
    }

    private static int version(Path archivo) {
        Matcher m = VERSION.matcher(archivo.getFileName().toString());
        return m.matches() ? Integer.parseInt(m.group(1)) : -1;
    }

    private static String leer(Path archivo) {
        try {
            return Files.readString(archivo);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
