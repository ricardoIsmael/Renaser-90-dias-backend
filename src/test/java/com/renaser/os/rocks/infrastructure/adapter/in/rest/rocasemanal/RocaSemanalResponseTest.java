package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocasemanal;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanal;
import com.renaser.os.rocks.domain.model.rocasemanal.RocaSemanalId;
import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Esta clase existe por un solo motivo: que nadie borre {@code accionesCriticas} del JSON sin leer
 * primero por que sigue ahi.
 *
 * <p>La tabla {@code acciones_criticas} se borro (V62) y el dominio no tiene acciones en la semana.
 * El campo del JSON <b>no</b> se fue con ellas, y no es un descuido: la app no usa
 * {@code expo-updates}, o sea que <b>no hay actualizacion por aire</b>. Quien tenga instalado un
 * build anterior al 2026-09-22 corre un esquema donde el campo es obligatorio, y si el servidor
 * deja de mandarlo se queda sin ver su plan semanal — sin poder hacer nada al respecto salvo
 * reinstalar.
 *
 * <p>Si alguien "limpia" el campo, este test se cae y el mensaje lo manda a leer esto.
 */
@DisplayName("RocaSemanalResponse: compatibilidad con la app instalada")
class RocaSemanalResponseTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    @Test
    @DisplayName("sigue mandando accionesCriticas vacia: los builds viejos la exigen para parsear")
    void mandaLaListaVaciaYNoLaOmite() {
        RocaSemanal roca = RocaSemanal.planificar(RocaSemanalId.of(UUID.randomUUID()),
                RocaMaestraId.of(UUID.randomUUID()), 3, "Bajar a 81,6 kg", null, null, null, CLOCK);

        RocaSemanalResponse respuesta = RocaSemanalResponse.from(roca);

        assertThat(respuesta.accionesCriticas())
                .as("no puede ser null: el esquema viejo de la app espera un arreglo, no ausencia")
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("el resto del objetivo semanal viaja tal cual")
    void llevaLosCamposDelObjetivo() {
        RocaSemanalId id = RocaSemanalId.of(UUID.randomUUID());
        RocaMaestraId maestra = RocaMaestraId.of(UUID.randomUUID());
        RocaSemanal roca = RocaSemanal.planificar(id, maestra, 3, "Bajar a 81,6 kg", "viajes",
                "entrenar en el hotel", 7, CLOCK);

        RocaSemanalResponse respuesta = RocaSemanalResponse.from(roca);

        assertThat(respuesta.id()).isEqualTo(id.value());
        assertThat(respuesta.rocaMaestraId()).isEqualTo(maestra.value());
        assertThat(respuesta.numeroSemana()).isEqualTo(3);
        assertThat(respuesta.titulo()).isEqualTo("Bajar a 81,6 kg");
        assertThat(respuesta.obstaculo()).isEqualTo("viajes");
        assertThat(respuesta.contingencia()).isEqualTo("entrenar en el hotel");
        assertThat(respuesta.autoevaluacionInicio()).isEqualTo(7);
        assertThat(respuesta.autoevaluacionFin()).isNull();
    }
}
