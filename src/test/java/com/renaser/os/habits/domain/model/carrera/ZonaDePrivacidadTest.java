package com.renaser.os.habits.domain.model.carrera;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/** Spec 042, FR-043: el servidor quita la zona de privacidad (200 m en cada punta) antes de guardar la ruta. */
public class ZonaDePrivacidadTest {

    /** Una ruta de ~1,83 km por Lima, codificada como la manda el teléfono (sin recortar). */
    static final String RUTA_COMPLETA = "bxohAplfuMSCS@UES@UGS?UGSASISASKSCSKSESMSEQMSGQMQGQQQGQQQIOQQKOQOKOSMIOSMMMSMKMUKKMSKMKSIMKSIMISIMGSIKGSGKESEKGQCKEQCICQCICOAGAOAG?O?E?M?C@M@C@K@ABI@ADIB?DGB@FED@FEFBFCFDHAFDJAHFH?JHJ@JHL@JJLBLJLDNLLDNNNDNNNFPPNHPNPHPRPHPRRHPRRJPRRJRTRJRRRLRRTLRRRLTRRLTRRJTTRJTRRJTRRHRRTHRPTHRPRHRNRFRNRFRLRDPLRBPLRBPHPBPHN?PHP?NDN?NDNALBNCLBLEL@LEJ?JGJ?JIJAHIHCHKHCFKHEFMDEFODGDODGBQ";
    /**
     * Lo que da el algoritmo de la app ({@code recortarPrivacidad} + {@code codificarPolyline} de {@code rutaCodificada.ts}) sobre
     * la misma ruta decodificada: el servidor tiene que dar EXACTAMENTE lo mismo.
     */
    static final String RECORTADA_POR_LA_APP = "pmohAlifuMECSGQMQGQQQGQQQIOQQKOQOKOSMIOSMMMSMKMUKKMSKMKSIMKSIMISIMGSIKGSGKESEKGQCKEQCICQCICOAGAOAG?O?E?M?C@M@C@K@ABI@ADIB?DGB@FED@FEFBFCFDHAFDJAHFH?JHJ@JHL@JJLBLJLDNLLDNNNDNNNFPPNHPNPHPRPHPRRHPRRJPRRJRTRJRRRLRRTLRRRLTRRLTRRJTTRJTRRJTRRHRRTHRPTHRPRHRNRFRNRFRLRDPLRBPLRBPHPBPHN?PHP?NDN?@?";

    /** Para las pruebas de otros paquetes. */
    public static String rutaCompleta() {
        return RUTA_COMPLETA;
    }

    public static String recortadaPorLaApp() {
        return RECORTADA_POR_LA_APP;
    }

    // spec 042 FR-043
    @Test
    @DisplayName("una ruta completa queda sin sus primeros ni sus últimos 200 m, cortada en el punto exacto")
    void cortaEnElPuntoExacto() {
        List<double[]> completa = PolylineDeGoogle.decodificar(RUTA_COMPLETA);

        RutaDeCarrera guardada = new RutaEnviada(RUTA_COMPLETA, completa.size()).sinLaZonaDePrivacidad().orElseThrow();

        List<double[]> recortada = PolylineDeGoogle.decodificar(guardada.polyline());
        assertThat(guardada.polyline()).isEqualTo(RECORTADA_POR_LA_APP);
        assertThat(guardada.puntos()).isEqualTo(recortada.size()).isEqualTo(139);
        // El primer punto guardado está a 200 m del inicio por la ruta (±1,5 m del redondeo a 1e-5), no en la puerta de casa.
        assertThat(largoHasta(completa, recortada.getFirst())).isCloseTo(ZonaDePrivacidad.METROS, within(1.5));
        assertThat(largoDesde(completa, recortada.getLast())).isCloseTo(ZonaDePrivacidad.METROS, within(1.5));
        assertThat(guardada.largoEnMetros()).isCloseTo(PolylineDeGoogle.largoEnMetros(RUTA_COMPLETA) - 400, within(3.0));
        assertThat(recortada).noneMatch(p -> mismoPunto(p, completa.getFirst()) || mismoPunto(p, completa.getLast()));
    }

    // spec 042 FR-043
    @Test
    @DisplayName("una ruta de menos de 400 m no deja nada que guardar")
    void menosDe400MetrosQuedaSinRuta() {
        List<double[]> completa = PolylineDeGoogle.decodificar(RUTA_COMPLETA);
        List<double[]> corta = new ArrayList<>(completa.subList(0, 25)); // ~250 m
        String polyline = PolylineDeGoogle.codificar(corta);

        assertThat(PolylineDeGoogle.largoEnMetros(polyline)).isLessThan(400);
        assertThat(new RutaEnviada(polyline, corta.size()).sinLaZonaDePrivacidad()).isEmpty();
    }

    // spec 042 FR-043
    @Test
    @DisplayName("codificar y decodificar dan la misma ruta; los topes de lo guardado se aplican después de recortar")
    void codificarYTopes() {
        assertThat(PolylineDeGoogle.codificar(PolylineDeGoogle.decodificar(RUTA_COMPLETA))).isEqualTo(RUTA_COMPLETA);
        assertThatThrownBy(() -> new RutaEnviada("_p~iF~ps|U_ulL", 2).sinLaZonaDePrivacidad()).hasMessageContaining("no se puede leer");
        assertThatThrownBy(() -> new RutaEnviada("x".repeat(RutaEnviada.BYTES_MAXIMOS + 1), 10))
                .hasMessageContaining("demasiado larga");
        assertThat(new RutaEnviada(RUTA_COMPLETA, 180).toString()).doesNotContain(RUTA_COMPLETA.substring(0, 8));
    }

    private static boolean mismoPunto(double[] a, double[] b) {
        return Math.abs(a[0] - b[0]) < 1e-9 && Math.abs(a[1] - b[1]) < 1e-9;
    }

    /** Lo que mide la ruta completa desde su inicio hasta el punto más cercano a {@code p} sobre ella. */
    private static double largoHasta(List<double[]> ruta, double[] p) {
        double mejor = Double.MAX_VALUE;
        double resultado = 0;
        double acumulado = 0;
        for (int i = 1; i < ruta.size(); i++) {
            double[] a = ruta.get(i - 1);
            double[] b = ruta.get(i);
            double tramo = PolylineDeGoogle.haversine(a, b);
            double hastaA = PolylineDeGoogle.haversine(a, p);
            double distancia = hastaA + PolylineDeGoogle.haversine(p, b) - tramo;
            if (distancia < mejor) {
                mejor = distancia;
                resultado = acumulado + hastaA;
            }
            acumulado += tramo;
        }
        return resultado;
    }

    private static double largoDesde(List<double[]> ruta, double[] p) {
        return PolylineDeGoogle.largoEnMetros(ruta) - largoHasta(ruta, p);
    }
}
