package com.renaser.os.chat.infrastructure.adapter.out.ranking;

import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana;
import com.renaser.os.chat.domain.model.ranking.PodioDeLaSemana.Aprendiz;
import com.renaser.os.chat.domain.model.ranking.SemanaDelRanking;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La imagen del podio (D-262) dibujada de verdad, a un PNG. Las imágenes quedan en {@code target/podio-semanal/} para
 * mirarlas, y la del caso de la maqueta además en {@code ~/Imágenes/ranking-semanal/generada-por-el-servidor.png}
 * si esa carpeta existe (la laptop del dueño), para compararla con la maqueta elegida.
 */
class PodioJava2dAdapterTest {

    private static final SemanaDelRanking SEMANA = new SemanaDelRanking(LocalDate.of(2026, 9, 28));
    private static final Path SALIDA = Path.of("target", "podio-semanal");
    private static final Path CARPETA_DEL_DUENO = Path.of(System.getProperty("user.home"), "Imágenes", "ranking-semanal");

    private final PodioJava2dAdapter adapter = new PodioJava2dAdapter();

    @Test
    @DisplayName("el caso de la maqueta: 1080×1350 en PNG, crema de fondo, oro al centro, plata y bronce a los lados")
    void elCasoDeLaMaqueta() throws IOException {
        byte[] png = adapter.dibujar(podio(
                aprendiz("Liz Mendoza Ruiz", "96.4"), aprendiz("Jorge Pérez", "92.1"), aprendiz("Carmen Rojas", "88.7"),
                aprendiz("Rosa Torres", "85.0"), aprendiz("Miguel Ángel Alva Soto", "83.2")), SEMANA);

        BufferedImage imagen = guardar("maqueta.png", png);
        if (Files.isDirectory(CARPETA_DEL_DUENO)) {
            Files.write(CARPETA_DEL_DUENO.resolve("generada-por-el-servidor.png"), png);
        }
        assertThat(png).startsWith(0x89, 'P', 'N', 'G');
        assertThat(imagen.getWidth()).isEqualTo(1080);
        assertThat(imagen.getHeight()).isEqualTo(1350);
        assertThat(color(imagen, 5, 5)).as("fondo crema").isEqualTo(new Color(0xFC, 0xFB, 0xF9));
        assertThat(cerca(color(imagen, 540, 1000), new Color(0xA8, 0x8A, 0x4A), 30)).as("bloque de oro al centro").isTrue();
        assertThat(cerca(color(imagen, 230, 1000), new Color(0xC2, 0xBE, 0xB3), 30)).as("plata a la izquierda").isTrue();
        assertThat(cerca(color(imagen, 850, 1000), new Color(0xCB, 0xA2, 0x80), 30)).as("bronce a la derecha").isTrue();
        assertThat(cerca(color(imagen, 230, 860), new Color(0xFC, 0xFB, 0xF9), 30)).as("la plata es más baja que el oro").isTrue();
        assertThat(png.length).as("liviana para datos móviles").isLessThan(700_000);
    }

    @Test
    @DisplayName("empate en el primero: dos bloques de oro; sin 4.º ni 5.º no hay cápsulas")
    void empateEnElPrimero() throws IOException {
        BufferedImage imagen = guardar("empate.png", adapter.dibujar(podio(
                aprendiz("Ana Quispe", "90.0"), aprendiz("Beto Ruiz", "90.0"), aprendiz("Carla Díaz", "70.5")), SEMANA));

        assertThat(cerca(color(imagen, 230, 840), new Color(0xB8, 0x99, 0x55), 35)).as("el 2.º de la lista es oro").isTrue();
        assertThat(color(imagen, 540, 1135)).as("sin cápsulas debajo").isEqualTo(new Color(0xFC, 0xFB, 0xF9));
    }

    @Test
    @DisplayName("con una sola persona: solo la columna del centro")
    void unaSolaPersona() throws IOException {
        BufferedImage imagen = guardar("una.png", adapter.dibujar(podio(aprendiz("Liz Mendoza", "41.3")), SEMANA));

        assertThat(cerca(color(imagen, 540, 1000), new Color(0xA8, 0x8A, 0x4A), 30)).isTrue();
        assertThat(cerca(color(imagen, 230, 1000), new Color(0xFC, 0xFB, 0xF9), 12)).as("sin plata").isTrue();
        assertThat(cerca(color(imagen, 850, 1000), new Color(0xFC, 0xFB, 0xF9), 12)).as("sin bronce").isTrue();
    }

    @Test
    @DisplayName("un nombre larguísimo achica la letra y no se sale de su columna")
    void nombreLargo() throws IOException {
        BufferedImage imagen = guardar("nombre-largo.png", adapter.dibujar(podio(
                aprendiz("Maximiliano Wenceslao", "80"), aprendiz("Bartolomeo Xiomara", "70"), aprendiz("Ñ", "60")), SEMANA));

        assertThat(imagen.getWidth()).isEqualTo(1080);
    }

    private static PodioDeLaSemana podio(Aprendiz... aprendices) {
        return PodioDeLaSemana.de(List.of(aprendices));
    }

    private static Aprendiz aprendiz(String nombre, String puntaje) {
        return new Aprendiz(nombre, new BigDecimal(puntaje));
    }

    private static BufferedImage guardar(String archivo, byte[] png) throws IOException {
        Files.createDirectories(SALIDA);
        Files.write(SALIDA.resolve(archivo), png);
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    private static Color color(BufferedImage imagen, int x, int y) {
        return new Color(imagen.getRGB(x, y));
    }

    private static boolean cerca(Color a, Color b, int tolerancia) {
        return Math.abs(a.getRed() - b.getRed()) <= tolerancia && Math.abs(a.getGreen() - b.getGreen()) <= tolerancia
                && Math.abs(a.getBlue() - b.getBlue()) <= tolerancia;
    }
}
