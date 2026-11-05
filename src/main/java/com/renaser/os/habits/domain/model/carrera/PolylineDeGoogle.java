package com.renaser.os.habits.domain.model.carrera;

import java.util.ArrayList;
import java.util.List;

/**
 * Decodifica una ruta escrita con el algoritmo de polilíneas de Google (el mismo que usa el teléfono, FR-040) y mide su largo
 * con la fórmula de haversine. El servidor no dibuja nada: solo la decodifica para comparar su largo con la distancia declarada
 * (FR-025). Una cadena que no se puede leer es un error de entrada, no una carrera dudosa.
 */
public final class PolylineDeGoogle {

    private static final double RADIO_DE_LA_TIERRA_M = 6_371_008.8;

    private PolylineDeGoogle() {
    }

    /** Los puntos {@code [latitud, longitud]} en grados. */
    public static List<double[]> decodificar(String polyline) {
        List<double[]> puntos = new ArrayList<>();
        int[] indice = {0};
        long latitud = 0;
        long longitud = 0;
        while (indice[0] < polyline.length()) {
            latitud += siguiente(polyline, indice);
            longitud += siguiente(polyline, indice);
            puntos.add(new double[]{latitud / 1e5, longitud / 1e5});
        }
        return puntos;
    }

    /** El largo de la ruta en metros: la suma de las distancias de haversine entre puntos seguidos. */
    public static double largoEnMetros(String polyline) {
        List<double[]> puntos = decodificar(polyline);
        double total = 0;
        for (int i = 1; i < puntos.size(); i++) {
            total += haversine(puntos.get(i - 1), puntos.get(i));
        }
        return total;
    }

    private static long siguiente(String polyline, int[] indice) {
        long resultado = 0;
        int desplazamiento = 0;
        int byteLeido;
        do {
            if (indice[0] >= polyline.length()) {
                throw new IllegalArgumentException("La ruta no se puede leer. Vuelve a enviarla.");
            }
            byteLeido = polyline.charAt(indice[0]++) - 63;
            if (byteLeido < 0 || byteLeido > 63) {
                throw new IllegalArgumentException("La ruta no se puede leer. Vuelve a enviarla.");
            }
            resultado |= (long) (byteLeido & 0x1f) << desplazamiento;
            desplazamiento += 5;
        } while (byteLeido >= 0x20 && desplazamiento < 64);
        return (resultado & 1) != 0 ? ~(resultado >> 1) : resultado >> 1;
    }

    private static double haversine(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * RADIO_DE_LA_TIERRA_M * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
