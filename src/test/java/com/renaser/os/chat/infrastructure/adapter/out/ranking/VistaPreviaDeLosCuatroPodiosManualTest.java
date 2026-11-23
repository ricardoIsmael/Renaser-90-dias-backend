package com.renaser.os.chat.infrastructure.adapter.out.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana;
import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Aprendiz;
import com.renaser.os.chat.domain.model.ranking.SemanaDelRanking;
import com.renaser.os.chat.domain.model.ranking.TextoDelPodio;
import com.renaser.os.chat.domain.model.ranking.TextoDelPodio.Plantilla;
import com.renaser.os.chat.domain.model.ranking.TipoDeRankingSemanal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

/**
 * Ayuda para REVISAR a ojo los cuatro podios antes de publicar (D-367). <b>Apagada por defecto</b>: no corre en {@code verificar} ni
 * en el CI. Se prende con {@code -Dpodio.vista=<carpeta>} y deja ahí {@code general.png}, {@code coherencia.png},
 * {@code kilometros.png}, {@code gimnasio.png} (con el {@link PodioJava2dAdapter} real y datos inventados, con un empate) y
 * {@code textos.txt} con las tres plantillas de cada ranking.
 *
 * <pre>
 * ~/.config/renaser-e2e/herramientas/pesado.sh ./mvnw -q test -Dtest=VistaPreviaDeLosCuatroPodiosManualTest -Dpodio.vista=/ruta/podio-vista
 * </pre>
 */
@EnabledIfSystemProperty(named = "podio.vista", matches = ".+")
class VistaPreviaDeLosCuatroPodiosManualTest {

    private static final SemanaDelRanking SEMANA = new SemanaDelRanking(LocalDate.of(2026, 11, 16));

    @Test
    @DisplayName("genera las cuatro imágenes y los doce textos (tres por ranking) en la carpeta de -Dpodio.vista")
    void genera() throws Exception {
        Path carpeta = Path.of(System.getProperty("podio.vista"));
        Files.createDirectories(carpeta);
        PodioJava2dAdapter adaptador = new PodioJava2dAdapter();
        StringBuilder textos = new StringBuilder();
        for (TipoDeRankingSemanal tipo : TipoDeRankingSemanal.values()) {
            PodioDeLaSemana podio = PodioDeLaSemana.de(tipo, filasDe(tipo));
            BufferedImage imagen = ImageIO.read(new ByteArrayInputStream(adaptador.dibujar(podio, SEMANA)));
            ImageIO.write(imagen, "png", carpeta.resolve(tipo.codigo() + ".png").toFile());
            textos.append("==== ").append(tipo.rotulo()).append(" ====\n\n");
            for (Plantilla plantilla : Plantilla.values()) {
                textos.append("--- Plantilla ").append(plantilla).append(" ---\n")
                        .append(TextoDelPodio.para(podio, semanaConPlantilla(tipo, plantilla))).append("\n\n");
            }
        }
        Files.writeString(carpeta.resolve("textos.txt"), textos.toString(), StandardCharsets.UTF_8);
    }

    private static SemanaDelRanking semanaConPlantilla(TipoDeRankingSemanal tipo, Plantilla plantilla) {
        SemanaDelRanking semana = SEMANA;
        while (TextoDelPodio.plantillaDe(semana, tipo) != plantilla) {
            semana = new SemanaDelRanking(semana.lunes().plusWeeks(1));
        }
        return semana;
    }

    /** Cinco nombres, con un empate en el segundo puesto. */
    private static List<Aprendiz> filasDe(TipoDeRankingSemanal tipo) {
        String[] nombres = {"Liz Mendoza Rojas", "Jorge Pérez Castillo", "Rosa Quispe Huamán", "María Fernanda Ñahui Salas", "Carlos Díaz"};
        String[] valores = switch (tipo) {
            case GENERAL -> new String[]{"96.4", "92.1", "92.1", "85.0", "78.3"};
            case COHERENCIA -> new String[]{"100.0", "93.8", "93.8", "81.3", "70.0"};
            case KILOMETROS -> new String[]{"42.5", "38.2", "38.2", "21.0", "12.7"};
            case GIMNASIO -> new String[]{"6", "5", "5", "4", "3"};
        };
        return java.util.stream.IntStream.range(0, nombres.length)
                .mapToObj(i -> new Aprendiz(nombres[i], new BigDecimal(valores[i]))).toList();
    }
}
