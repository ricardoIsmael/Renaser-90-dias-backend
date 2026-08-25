package com.renaser.os.rocks.domain.model.rocasemanal;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocaSemanalTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static RocaMaestraId maestra() {
        return RocaMaestraId.of(UUID.randomUUID());
    }

    private static List<AccionCritica> tresAcciones() {
        return List.of(new AccionCritica(1, "uno"), new AccionCritica(2, "dos"), new AccionCritica(3, "tres"));
    }

    @Test
    void planificarConTresAccionesCreaLaRocaAbiertaSinRevision() {
        RocaSemanal roca = RocaSemanal.planificar(maestra(), 3, "Titulo", tresAcciones(), "obstaculo",
                "contingencia", 7, CLOCK);

        assertThat(roca.numeroSemana()).isEqualTo(3);
        assertThat(roca.acciones()).hasSize(3);
        assertThat(roca.autoevaluacionFin()).isNull();
        assertThat(roca.bloqueoPrincipal()).isNull();
        assertThat(roca.creadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void rechazaMenosDeTresAcciones() {
        List<AccionCritica> dos = List.of(new AccionCritica(1, "uno"), new AccionCritica(2, "dos"));
        assertThatThrownBy(() -> RocaSemanal.planificar(maestra(), 1, "T", dos, null, null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaOrdenesRepetidos() {
        List<AccionCritica> repetidas = List.of(new AccionCritica(1, "a"), new AccionCritica(1, "b"),
                new AccionCritica(3, "c"));
        assertThatThrownBy(() -> RocaSemanal.planificar(maestra(), 1, "T", repetidas, null, null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numeroSemanaFueraDeRangoEsInvalido() {
        assertThatThrownBy(() -> RocaSemanal.planificar(maestra(), 14, "T", tresAcciones(), null, null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RocaSemanal.planificar(maestra(), 0, "T", tresAcciones(), null, null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarPlanificacionSoloTocaLosCamposNoNulos() {
        RocaSemanal roca = RocaSemanal.planificar(maestra(), 1, "Original", tresAcciones(), "obs", "cont", 5, CLOCK);

        roca.actualizarPlanificacion("Nuevo titulo", null, null, null, null, CLOCK);

        assertThat(roca.titulo()).isEqualTo("Nuevo titulo");
        assertThat(roca.obstaculo()).isEqualTo("obs");
        assertThat(roca.autoevaluacionInicio()).isEqualTo(5);
        assertThat(roca.acciones()).hasSize(3);
    }

    @Test
    void registrarRevisionEsIdempotenteYSobreescribe() {
        RocaSemanal roca = RocaSemanal.planificar(maestra(), 1, "T", tresAcciones(), null, null, null, CLOCK);

        roca.registrarRevision(8, "bloqueo original", "correccion original", CLOCK);
        roca.registrarRevision(9, "bloqueo nuevo", "correccion nueva", CLOCK);

        assertThat(roca.autoevaluacionFin()).isEqualTo(9);
        assertThat(roca.bloqueoPrincipal()).isEqualTo("bloqueo nuevo");
        assertThat(roca.correccion()).isEqualTo("correccion nueva");
    }

    @Test
    void autoevaluacionFueraDeRangoEsInvalida() {
        RocaSemanal roca = RocaSemanal.planificar(maestra(), 1, "T", tresAcciones(), null, null, null, CLOCK);
        assertThatThrownBy(() -> roca.registrarRevision(11, "b", "c", CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
