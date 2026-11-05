package com.renaser.os.habits.domain.model.carrera;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Carreras de prueba de la spec 042: por defecto, 5 km en 30 minutos activos (10 km/h), 5 tramos de 6 minutos, sin ruta. */
public final class CarrerasDePrueba {

    public static final Instant INICIO = Instant.parse("2026-11-05T11:00:00Z");

    private CarrerasDePrueba() {
    }

    public static DatosDeCarrera de(UUID carreraId, String metros, int segundosActivos) {
        return new DatosDeCarrera(carreraId, INICIO, INICIO.plusSeconds(segundosActivos + 60L), segundosActivos,
                new BigDecimal(metros), 12, null, 300, tramosParejos(new BigDecimal(metros), segundosActivos), null, null, "1.8.0");
    }

    public static DatosDeCarrera cincoKm() {
        return de(UUID.randomUUID(), "5000", 1800);
    }

    /** Un tramo por km completo, todos al mismo ritmo, y el parcial al final si sobra distancia. */
    public static List<Tramo> tramosParejos(BigDecimal metros, int segundosActivos) {
        double segundosPorMetro = segundosActivos / metros.doubleValue();
        int completos = metros.intValue() / 1000;
        List<Tramo> tramos = new ArrayList<>();
        for (int km = 1; km <= completos; km++) {
            tramos.add(new Tramo(km, (int) Math.round(1000 * segundosPorMetro)));
        }
        double resto = metros.doubleValue() - completos * 1000.0;
        if (resto >= 1) {
            tramos.add(new Tramo(completos + 1, Math.max(1, (int) Math.round(resto * segundosPorMetro))));
        }
        return tramos;
    }
}
