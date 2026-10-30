package com.renaser.os.rocks.application.services;

import com.renaser.os.habits.api.EvaluacionDelDiaPort.MarcaDeCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.AccionDelCierre;
import com.renaser.os.rocks.api.CierresDelDiaFinder.DiaDelCierre;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.cumplida;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendiente;
import static org.assertj.core.api.Assertions.assertThat;

/** La traduccion pura del dominio a lo que se ve del cierre (spec 017). */
class ArmadoDelCierreTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);

    @Test
    @DisplayName("las acciones salen por hora de inicio y las que no tienen hora, al final, por posicion")
    void ordenPorHora() {
        RocaDiaria sinHora = pendiente(HOY, 1, "Sin hora", null);
        RocaDiaria tarde = pendiente(HOY, 2, "Tarde", LocalTime.of(20, 30));
        RocaDiaria temprano = cumplida(HOY, 3, "Temprano", LocalTime.of(10, 0), 5);

        List<AccionDelCierre> acciones = ArmadoDelCierre.acciones(List.of(sinHora, tarde, temprano));

        assertThat(acciones).extracting(AccionDelCierre::titulo).containsExactly("Temprano", "Tarde", "Sin hora");
        assertThat(acciones.get(0).cumplida()).isTrue();
        assertThat(acciones.get(0).cumplidaEn()).isNotNull();
        assertThat(acciones.get(1).cumplidaEn()).isNull();
    }

    @Test
    @DisplayName("hoy sin marca es «abierto»; con marca, «cerrado» con la hora del primer cierre y la nota del dia")
    void estadoYMarca() {
        Instant cierre = Instant.parse("2026-10-29T03:00:00Z");

        DiaDelCierre abierto = ArmadoDelCierre.dia(HOY, HOY, List.of(), null);
        DiaDelCierre cerrado = ArmadoDelCierre.dia(HOY, HOY, List.of(), new MarcaDeCierre(HOY, "Descansé", cierre));
        DiaDelCierre sinCerrar = ArmadoDelCierre.dia(HOY.minusDays(1), HOY, List.of(), null);

        assertThat(abierto.estado()).isEqualTo("ABIERTO");
        assertThat(abierto.cerradoEn()).isNull();
        assertThat(cerrado.estado()).isEqualTo("CERRADO");
        assertThat(cerrado.cerradoEn()).isEqualTo(cierre);
        assertThat(cerrado.nota()).isEqualTo("Descansé");
        assertThat(sinCerrar.estado()).isEqualTo("SIN_CERRAR");
    }

    @Test
    @DisplayName("el avance de la semana cuenta cumplidas sobre planificadas, y sin planificadas es null (nunca «0 de 0»)")
    void avance() {
        assertThat(ArmadoDelCierre.avance(List.of())).isNull();
        assertThat(ArmadoDelCierre.avance(List.of(cumplida(HOY, 1, "a", null, 5), pendiente(HOY, 2, "b", null), pendiente(HOY, 3, "c", null))))
                .satisfies(a -> {
                    assertThat(a.cumplidas()).isEqualTo(1);
                    assertThat(a.planificadas()).isEqualTo(3);
                });
    }
}
