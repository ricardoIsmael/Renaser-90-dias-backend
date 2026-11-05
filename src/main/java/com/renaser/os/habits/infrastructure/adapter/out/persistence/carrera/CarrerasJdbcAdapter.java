package com.renaser.os.habits.infrastructure.adapter.out.persistence.carrera;

import com.renaser.os.habits.application.ports.out.carrera.CarrerasPort;
import com.renaser.os.habits.domain.model.carrera.Carrera;
import com.renaser.os.habits.domain.model.carrera.EfectoEnElHabito;
import com.renaser.os.habits.domain.model.carrera.RutaDeCarrera;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link CarrerasPort} con {@link JdbcClient} sobre {@code carreras} (V170). JDBC y no JPA: la idempotencia necesita
 * {@code INSERT … ON CONFLICT DO NOTHING}, que dice si insertó sin lanzar una excepción que marque la transacción para deshacer
 * (con JPA, el choque con el índice único tumbaría la transacción entera y no se podría leer lo guardado después).
 */
@Component
class CarrerasJdbcAdapter implements CarrerasPort {

    private static final String INSERTAR = """
            INSERT INTO renaser.carreras (id, participante_id, carrera_id, huella, fecha, iniciada_en, terminada_en,
                   duracion_activa_seg, distancia_m, subida_m, pasos, calorias_aprox, metros_sin_gps, tramos, ruta_polyline,
                   ruta_puntos, cuenta_para_el_habito, motivo_no_cuenta, por_revisar, motivo_revision, resultado_habito,
                   registro_habito_id, km_del_dia, origen_del_dia, puntos_otorgados, app_version, creada_en)
            VALUES (:id, :participante, :carreraId, :huella, :fecha, :iniciadaEn, :terminadaEn, :duracion, :distancia, :subida,
                    :pasos, :calorias, :sinGps, CAST(:tramos AS jsonb), :polyline, :puntos, :cuenta, :motivoNoCuenta, :porRevisar,
                    :motivoRevision, :resultado, :registro, :kmDelDia, :origenDelDia, :puntosOtorgados, :appVersion, :creadaEn)
            ON CONFLICT (participante_id, carrera_id) DO NOTHING
            """;

    private static final String ANOTAR_EFECTO = """
            UPDATE renaser.carreras
               SET resultado_habito = :resultado, registro_habito_id = :registro, km_del_dia = :kmDelDia,
                   origen_del_dia = :origenDelDia, puntos_otorgados = :puntos
             WHERE id = :id
            """;

    private static final String KM_QUE_CUENTAN = """
            SELECT COALESCE(SUM(distancia_m), 0) / 1000 AS km
              FROM renaser.carreras
             WHERE participante_id = :participante AND fecha = :fecha AND cuenta_para_el_habito
            """;

    private static final String RUTA = """
            SELECT ruta_polyline, ruta_puntos FROM renaser.carreras
             WHERE participante_id = :participante AND carrera_id = :carreraId AND ruta_polyline IS NOT NULL
            """;

    private final JdbcClient jdbc;

    CarrerasJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean guardarSiEsNueva(Carrera carrera) {
        var datos = carrera.datos();
        var ruta = datos.ruta();
        var evaluacion = carrera.evaluacion();
        var efecto = carrera.efecto();
        int filas = jdbc.sql(INSERTAR)
                .param("id", carrera.id()).param("participante", carrera.participanteId().value())
                .param("carreraId", datos.carreraId()).param("huella", carrera.huella()).param("fecha", carrera.fecha())
                .param("iniciadaEn", Timestamp.from(datos.iniciadaEn())).param("terminadaEn", Timestamp.from(datos.terminadaEn()))
                .param("duracion", datos.duracionActivaSeg()).param("distancia", datos.distanciaMetros())
                .param("subida", datos.subidaMetros()).param("pasos", datos.pasos()).param("calorias", datos.caloriasAprox())
                .param("sinGps", datos.metrosSinGps()).param("tramos", FilaDeCarrera.tramosEnJson(datos.tramos()))
                .param("polyline", ruta == null ? null : ruta.polyline()).param("puntos", ruta == null ? null : ruta.puntos())
                .param("cuenta", evaluacion.cuenta()).param("motivoNoCuenta", evaluacion.motivoNoCuenta())
                .param("porRevisar", evaluacion.porRevisar()).param("motivoRevision", evaluacion.motivoRevision())
                .param("resultado", efecto.resultado().name()).param("registro", efecto.registroId())
                .param("kmDelDia", efecto.kmDelDia())
                .param("origenDelDia", efecto.origenDelDia() == null ? null : efecto.origenDelDia().name())
                .param("puntosOtorgados", efecto.puntos()).param("appVersion", datos.appVersion())
                .param("creadaEn", Timestamp.from(carrera.creadaEn()))
                .update();
        return filas == 1;
    }

    @Override
    public Optional<Carrera> de(UserId participante, UUID carreraId) {
        return jdbc.sql(FilaDeCarrera.SELECT + " WHERE participante_id = :participante AND carrera_id = :carreraId")
                .param("participante", participante.value()).param("carreraId", carreraId)
                .query(FilaDeCarrera::aCarrera).optional();
    }

    @Override
    public void anotarEfecto(UUID id, EfectoEnElHabito efecto) {
        jdbc.sql(ANOTAR_EFECTO).param("id", id).param("resultado", efecto.resultado().name())
                .param("registro", efecto.registroId()).param("kmDelDia", efecto.kmDelDia())
                .param("origenDelDia", efecto.origenDelDia() == null ? null : efecto.origenDelDia().name())
                .param("puntos", efecto.puntos())
                .update();
    }

    @Override
    public BigDecimal kilometrosQueCuentan(UserId participante, LocalDate fecha) {
        return jdbc.sql(KM_QUE_CUENTAN).param("participante", participante.value()).param("fecha", fecha)
                .query(BigDecimal.class).single();
    }

    @Override
    public Optional<RutaDeCarrera> rutaDe(UserId participante, UUID carreraId) {
        return jdbc.sql(RUTA).param("participante", participante.value()).param("carreraId", carreraId)
                .query((rs, fila) -> new RutaDeCarrera(rs.getString("ruta_polyline"), rs.getInt("ruta_puntos")))
                .optional();
    }
}
