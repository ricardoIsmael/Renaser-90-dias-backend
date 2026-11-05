package com.renaser.os.habits.domain.model.carrera;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * La zona de privacidad de una ruta (spec 042, FR-043): los primeros y los últimos 200 m de una carrera, que suelen ser la puerta
 * de la casa de alguien. La quita el SERVIDOR antes de guardar, no el teléfono: así, aunque una app modificada mande la ruta
 * entera, la base nunca guarda dónde empieza ni dónde termina una carrera. Se corta en el punto exacto (interpolando dentro del
 * tramo que cruza los 200 m), con el mismo algoritmo que tenía la app ({@code recortarPrivacidad}). Si después de cortar no quedan
 * al menos 2 puntos, no queda ruta. Pura.
 */
public final class ZonaDePrivacidad {

    /** Lo que se quita en cada punta de la ruta. */
    public static final double METROS = 200;

    private ZonaDePrivacidad() {
    }

    /** Los puntos {@code [latitud, longitud]} sin los primeros ni los últimos {@link #METROS}; vacío si no los tiene. */
    public static List<double[]> recortar(List<double[]> puntos) {
        if (puntos.size() < 2) {
            return List.of();
        }
        List<double[]> desdeElInicio = cortarDesde(puntos);
        if (desdeElInicio.size() < 2) {
            return List.of();
        }
        List<double[]> alReves = new ArrayList<>(desdeElInicio);
        Collections.reverse(alReves);
        List<double[]> recortada = new ArrayList<>(cortarDesde(alReves));
        Collections.reverse(recortada);
        return recortada;
    }

    private static List<double[]> cortarDesde(List<double[]> puntos) {
        double acumulado = 0;
        for (int i = 1; i < puntos.size(); i++) {
            double[] anterior = puntos.get(i - 1);
            double[] actual = puntos.get(i);
            double tramo = PolylineDeGoogle.haversine(anterior, actual);
            if (acumulado + tramo >= METROS) {
                double fraccion = tramo > 0 ? (METROS - acumulado) / tramo : 0;
                List<double[]> resto = new ArrayList<>(puntos.size() - i + 1);
                resto.add(interpolar(anterior, actual, fraccion));
                resto.addAll(puntos.subList(i, puntos.size()));
                return resto;
            }
            acumulado += tramo;
        }
        return List.of();
    }

    /** A distancias de pocos metros la línea recta basta (igual que la app). */
    private static double[] interpolar(double[] a, double[] b, double fraccion) {
        double t = Math.max(0, Math.min(1, fraccion));
        return new double[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t};
    }
}
