package com.renaser.os.habits.infrastructure.adapter.out.persistence.carrera;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.renaser.os.habits.domain.model.carrera.Carrera;
import com.renaser.os.habits.domain.model.carrera.DatosDeCarrera;
import com.renaser.os.habits.domain.model.carrera.EfectoEnElHabito;
import com.renaser.os.habits.domain.model.carrera.EvaluacionDeCarrera;
import com.renaser.os.habits.domain.model.carrera.ResultadoEnElHabito;
import com.renaser.os.habits.domain.model.carrera.RutaDeCarrera;
import com.renaser.os.habits.domain.model.carrera.Tramo;
import com.renaser.os.habits.domain.model.medicion.OrigenMedicion;
import com.renaser.os.shared.domain.UserId;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** La traducción a mano entre una fila de {@code carreras} y {@link Carrera} (regla 04: hay enums, jsonb y nulos que decidir). */
final class FilaDeCarrera {

    static final String SELECT = """
            SELECT id, participante_id, carrera_id, huella, fecha, iniciada_en, terminada_en, duracion_activa_seg, distancia_m,
                   subida_m, pasos, calorias_aprox, metros_sin_gps, tramos::text AS tramos, ruta_polyline, ruta_puntos,
                   cuenta_para_el_habito, motivo_no_cuenta, por_revisar, motivo_revision, resultado_habito, registro_habito_id,
                   km_del_dia, origen_del_dia, puntos_otorgados, app_version, creada_en
              FROM renaser.carreras""";

    private static final ObjectMapper JSON = new ObjectMapper();

    private FilaDeCarrera() {
    }

    static String tramosEnJson(List<Tramo> tramos) {
        var arreglo = JSON.createArrayNode();
        tramos.forEach(t -> arreglo.addObject().put("km", t.km()).put("segundos", t.segundos()));
        return arreglo.toString();
    }

    /**
     * La huella es la guardada, no la que se recalcularía: después de 180 días la ruta ya no está y los datos rehidratados no
     * tienen todo lo que entró en el cálculo.
     */
    static Carrera aCarrera(ResultSet rs, int fila) throws SQLException {
        DatosDeCarrera datos = new DatosDeCarrera(rs.getObject("carrera_id", UUID.class), instante(rs, "iniciada_en"),
                instante(rs, "terminada_en"), rs.getInt("duracion_activa_seg"), rs.getBigDecimal("distancia_m"),
                entero(rs, "subida_m"), entero(rs, "pasos"), entero(rs, "calorias_aprox"), tramos(rs.getString("tramos")),
                entero(rs, "metros_sin_gps"), ruta(rs), rs.getString("app_version"));
        EvaluacionDeCarrera evaluacion = new EvaluacionDeCarrera(rs.getBoolean("cuenta_para_el_habito"),
                rs.getString("motivo_no_cuenta"), rs.getBoolean("por_revisar"), rs.getString("motivo_revision"));
        String origen = rs.getString("origen_del_dia");
        EfectoEnElHabito efecto = new EfectoEnElHabito(ResultadoEnElHabito.valueOf(rs.getString("resultado_habito")),
                rs.getObject("registro_habito_id", UUID.class), rs.getBigDecimal("km_del_dia"),
                origen == null ? null : OrigenMedicion.valueOf(origen), rs.getInt("puntos_otorgados"));
        return new Carrera(rs.getObject("id", UUID.class), UserId.of(rs.getObject("participante_id", UUID.class)), datos,
                rs.getString("huella"), rs.getObject("fecha", LocalDate.class), evaluacion, efecto, instante(rs, "creada_en"));
    }

    private static RutaDeCarrera ruta(ResultSet rs) throws SQLException {
        String polyline = rs.getString("ruta_polyline");
        return polyline == null ? null : new RutaDeCarrera(polyline, rs.getInt("ruta_puntos"));
    }

    private static List<Tramo> tramos(String json) {
        try {
            List<Tramo> tramos = new ArrayList<>();
            for (JsonNode nodo : JSON.readTree(json)) {
                tramos.add(new Tramo(nodo.get("km").asInt(), nodo.get("segundos").asInt()));
            }
            return tramos;
        } catch (JsonProcessingException ilegible) {
            throw new IllegalStateException("tramos ilegibles en carreras: " + json, ilegible);
        }
    }

    private static java.time.Instant instante(ResultSet rs, String columna) throws SQLException {
        return rs.getObject(columna, OffsetDateTime.class).toInstant();
    }

    private static Integer entero(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }
}
