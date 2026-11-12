package com.renaser.os.onboarding.infrastructure.adapter.in.rest.caja;

import com.renaser.os.onboarding.application.ports.in.caja.ConsultarCajasUseCase.ExportacionDeCajas;
import com.renaser.os.onboarding.application.ports.in.caja.ResumenDeCaja;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El CSV de la Caja (spec 050, tramo F; SC-008): el de antes del streaming y el de ahora son el mismo, byte a byte, sea cual
 * sea el tamaño del lote en que se entregue, y se escribe por lotes sin juntarlos.
 *
 * <p>Los archivos dorados se capturaron con el código de antes (commit «Fijar con pruebas el CSV de Caja…»).
 */
class CsvDeCajasTest {

    static final Path DORADO = Path.of("src/test/resources/exportaciones/caja-dorado.csv");
    static final Path DORADO_DE_MUCHAS = Path.of("src/test/resources/exportaciones/caja-dorado-120-filas.csv");

    /** Una exportación que entrega esas filas en lotes de ese tamaño. */
    static ExportacionDeCajas enLotesDe(List<ResumenDeCaja> filas, int tamano) {
        return escritor -> {
            for (int desde = 0; desde < filas.size(); desde += tamano) {
                escritor.accept(filas.subList(desde, Math.min(filas.size(), desde + tamano)));
            }
        };
    }

    private static byte[] escribir(ExportacionDeCajas exportacion) throws IOException {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        CsvDeCajas.escribir(exportacion, salida);
        return salida.toByteArray();
    }

    // spec 050 FR-032, SC-008
    @ParameterizedTest
    @ValueSource(ints = {1, 3, 8, 200})
    @DisplayName("el CSV de ocho filas de todo tipo es idéntico, byte a byte, al de antes del streaming, en lotes de 1, 3, 8 o 200")
    void elCsvDeSiempre(int tamanoDelLote) throws IOException {
        byte[] actual = escribir(enLotesDe(FilasDeCajaParaLasPruebas.deSiempre(), tamanoDelLote));

        assertThat(actual).isEqualTo(Files.readAllBytes(DORADO));
    }

    // spec 050 FR-032, SC-008
    @ParameterizedTest
    @ValueSource(ints = {1, 7, 50, 200, 500})
    @DisplayName("el CSV de 120 filas es idéntico al de antes con cualquier tamaño de lote")
    void elCsvDeMuchasFilas(int tamanoDelLote) throws IOException {
        byte[] actual = escribir(enLotesDe(FilasDeCajaParaLasPruebas.varias(120), tamanoDelLote));

        assertThat(actual).isEqualTo(Files.readAllBytes(DORADO_DE_MUCHAS));
    }

    // spec 050 FR-032
    @Test
    @DisplayName("sin ninguna caja sale solo el BOM y la cabecera, y la exportación ni se pide hasta que se escribe")
    void sinCajas() throws IOException {
        byte[] actual = escribir(escritor -> { });

        String texto = new String(actual, StandardCharsets.UTF_8);
        assertThat(actual).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        assertThat(texto.substring(1)).startsWith("aprendizId;nombre;grupo;").endsWith("actualizadoEn\r\n");
        assertThat(texto.lines().count()).isEqualTo(1);
    }

    // spec 050 FR-032
    @Test
    @DisplayName("lo escrito sale antes de pedir el lote siguiente: la cabecera ya está escrita cuando llega el primer lote, y cada "
            + "lote se escribe y se vacía antes del próximo")
    void cadaLoteSaleAntesDelSiguiente() throws IOException {
        List<String> orden = new ArrayList<>();
        ByteArrayOutputStream destino = new ByteArrayOutputStream();
        OutputStream salida = new OutputStream() {
            @Override
            public void write(int b) {
                destino.write(b);
            }

            @Override
            public void write(byte[] b, int desde, int largo) {
                destino.write(b, desde, largo);
            }

            @Override
            public void flush() {
                orden.add("vaciar:" + destino.size());
            }
        };
        List<ResumenDeCaja> filas = FilasDeCajaParaLasPruebas.varias(6);
        ExportacionDeCajas lotes = escritor -> {
            orden.add("cabecera-escrita:" + (destino.size() > 0));
            for (int i = 0; i < filas.size(); i += 2) {
                orden.add("entrega-lote-" + i / 2 + ":" + destino.size());
                escritor.accept(filas.subList(i, i + 2));
            }
        };

        CsvDeCajas.escribir(lotes, salida);

        assertThat(orden.get(0)).as("la cabecera se vacía antes de pedir nada").startsWith("vaciar:");
        assertThat(orden).contains("cabecera-escrita:true");
        // Cada entrega de un lote ve ya escrito (y vaciado) todo lo anterior: los bytes crecen lote a lote.
        List<Integer> tamanosAlEntregar = orden.stream().filter(o -> o.startsWith("entrega-lote-"))
                .map(o -> Integer.parseInt(o.substring(o.indexOf(':') + 1))).toList();
        assertThat(tamanosAlEntregar).isSorted().doesNotHaveDuplicates();
        assertThat(orden.stream().filter(o -> o.startsWith("vaciar:")).count()).isEqualTo(4);
    }

    // spec 050 FR-032, SC-008
    @Test
    @DisplayName("no junta los lotes: con miles de filas, un lote ya escrito se puede recoger mientras se escribe el siguiente")
    void noRetieneLosLotesYaEscritos() throws Exception {
        List<WeakReference<List<ResumenDeCaja>>> yaEscritos = new ArrayList<>();
        int[] recogidos = {0};
        ExportacionDeCajas generadora = escritor -> {
            for (int lote = 0; lote < 40; lote++) {
                List<ResumenDeCaja> filas = FilasDeCajaParaLasPruebas.varias(200);
                yaEscritos.add(new WeakReference<>(filas));
                escritor.accept(filas);
                if (lote >= 3) {
                    // Los lotes de más de dos vueltas atrás ya no los retiene nadie: después de un GC no quedan vivos.
                    forzarElRecolector();
                    recogidos[0] = (int) yaEscritos.subList(0, lote - 2).stream().filter(r -> r.get() == null).count();
                }
            }
        };

        CsvDeCajas.escribir(generadora, OutputStream.nullOutputStream());

        // De los 37 lotes que ya habían pasado hace dos vueltas al final, casi todos se recogieron (el recolector puede dejar
        // alguno): si el escritor los juntara, ninguno se podría recoger.
        assertThat(recogidos[0]).as("lotes ya escritos que el recolector pudo soltar").isGreaterThan(30);
    }

    @Test
    @DisplayName("si el destino falla a mitad (el cliente cortó la descarga), el error de escritura sale tal cual, no se traga")
    void unFalloDeEscrituraSale() {
        OutputStream roto = new OutputStream() {
            private int escritos;

            @Override
            public void write(int b) throws IOException {
                if (++escritos > 500) {
                    throw new IOException("Broken pipe");
                }
            }
        };

        assertThatThrownBy(() -> CsvDeCajas.escribir(enLotesDe(FilasDeCajaParaLasPruebas.varias(50), 10), roto))
                .isInstanceOf(IOException.class).hasMessage("Broken pipe");
    }

    private static void forzarElRecolector() {
        for (int i = 0; i < 3; i++) {
            System.gc();
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }
}
