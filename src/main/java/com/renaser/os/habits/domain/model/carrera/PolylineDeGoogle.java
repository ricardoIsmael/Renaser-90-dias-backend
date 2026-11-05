package com.renaser.os.habits.domain.model.carrera;

import java.util.ArrayList;
import java.util.List;

/**
 * Lee y escribe una ruta con el algoritmo de polilíneas de Google (precisión 1e-5, el mismo que usa el teléfono, FR-040) y mide
 * su largo con la fórmula de haversine. El servidor no dibuja nada: la decodifica para quitarle la zona de privacidad antes de
 * guardarla (FR-043, {@link ZonaDePrivacidad}), la vuelve a codificar y compara su largo con la distancia declarada (FR-025). Una
 * cadena que no se puede leer es un error de entrada, no una carrera dudosa.
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

    /** Escribe los puntos {@code [latitud, longitud]} (en grados) redondeados a 1e-5, como el teléfono. */
    public static String codificar(List<double[]> puntos) {
        StringBuilder salida = new StringBuilder();
        long latitudPrevia = 0;
        long longitudPrevia = 0;
        for (double[] punto : puntos) {
            long latitud = Math.round(punto[0] * 1e5);
            long longitud = Math.round(punto[1] * 1e5);
            escribir(latitud - latitudPrevia, salida);
            escribir(longitud - longitudPrevia, salida);
            latitudPrevia = latitud;
            longitudPrevia = longitud;
        }
        return salida.toString();
    }

    /** El largo de la ruta en metros: la suma de las distancias de haversine entre puntos seguidos. */
    public static double largoEnMetros(String polyline) {
        return largoEnMetros(decodificar(polyline));
    }

    static double largoEnMetros(List<double[]> puntos) {
        double total = 0;
        for (int i = 1; i < puntos.size(); i++) {
            total += haversine(puntos.get(i - 1), puntos.get(i));
        }
        return total;
    }

    private static void escribir(long valor, StringBuilder salida) {
        long v = valor < 0 ? ~(valor << 1) : valor << 1;
        while (v >= 0x20) {
            salida.append((char) ((0x20 | (v & 0x1f)) + 63));
            v >>= 5;
        }
        salida.append((char) (v + 63));
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

    static double haversine(double[] a, double[] b) {
        double lat1 = Math.toRadians(a[0]);
        double lat2 = Math.toRadians(b[0]);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b[1] - a[1]);
        double h = Math.pow(Math.sin(dLat / 2), 2) + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(dLon / 2), 2);
        return 2 * RADIO_DE_LA_TIERRA_M * Math.asin(Math.min(1, Math.sqrt(h)));
    }
}
