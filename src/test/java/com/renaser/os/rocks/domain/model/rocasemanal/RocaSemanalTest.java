package com.renaser.os.rocks.domain.model.rocasemanal;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RocaSemanalTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static RocaMaestraId maestra() {
        return RocaMaestraId.of(UUID.randomUUID());
    }

    /** El id ya no lo sortea la factoria: entra por parametro (puerto IdGenerator). */
    private static RocaSemanalId unId() {
        return RocaSemanalId.of(UUID.randomUUID());
    }

    private static List<AccionCritica> tresAcciones() {
        return List.of(new AccionCritica(1, "uno"), new AccionCritica(2, "dos"), new AccionCritica(3, "tres"));
    }

    @Test
    void planificarConTresAccionesCreaLaRocaAbiertaSinRevision() {
        RocaSemanalId id = unId();
        RocaSemanal roca = RocaSemanal.planificar(id, maestra(), 3, "Titulo", tresAcciones(), "obstaculo",
                "contingencia", 7, CLOCK);

        assertThat(roca.id()).isEqualTo(id);
        assertThat(roca.numeroSemana()).isEqualTo(3);
        assertThat(roca.acciones()).hasSize(3);
        assertThat(roca.autoevaluacionFin()).isNull();
        assertThat(roca.bloqueoPrincipal()).isNull();
        assertThat(roca.creadoEn()).isEqualTo(CLOCK.now());
    }

    /**
     * > <b>Corregido el 2026-09-22.</b> Este test se llamaba {@code rechazaMenosDeTresAcciones} y
     * > verificaba lo contrario: que dos acciones fueran un error. Las acciones pasaron al objetivo
     * > diario (V61) y la semana quedo en su objetivo, asi que menos de tres —y ninguna— es lo
     * > normal ahora. Lo que sigue siendo error es pasarse de tres.
     */
    @Test
    void aceptaMenosDeTresAccionesYHastaNinguna() {
        List<AccionCritica> dos = List.of(new AccionCritica(1, "uno"), new AccionCritica(2, "dos"));

        assertThatCode(() -> RocaSemanal.planificar(unId(), maestra(), 1, "T", dos, null, null, null, CLOCK))
                .doesNotThrowAnyException();
        assertThatCode(() -> RocaSemanal.planificar(unId(), maestra(), 1, "T", List.of(), null, null, null, CLOCK))
                .doesNotThrowAnyException();
        assertThatCode(() -> RocaSemanal.planificar(unId(), maestra(), 1, "T", null, null, null, null, CLOCK))
                .doesNotThrowAnyException();
    }

    @Test
    void rechazaMasDeTresAcciones() {
        List<AccionCritica> cuatro = List.of(new AccionCritica(1, "a"), new AccionCritica(2, "b"),
                new AccionCritica(3, "c"), new AccionCritica(3, "d"));
        assertThatThrownBy(() -> RocaSemanal.planificar(unId(), maestra(), 1, "T", cuatro, null, null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rechazaOrdenesRepetidos() {
        List<AccionCritica> repetidas = List.of(new AccionCritica(1, "a"), new AccionCritica(1, "b"),
                new AccionCritica(3, "c"));
        assertThatThrownBy(() -> RocaSemanal.planificar(unId(), maestra(), 1, "T", repetidas, null, null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void numeroSemanaFueraDeRangoEsInvalido() {
        assertThatThrownBy(() -> RocaSemanal.planificar(unId(), maestra(), 14, "T", tresAcciones(), null, null,
                null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RocaSemanal.planificar(unId(), maestra(), 0, "T", tresAcciones(), null, null,
                null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarPlanificacionSoloTocaLosCamposNoNulos() {
        RocaSemanal roca = RocaSemanal.planificar(unId(), maestra(), 1, "Original", tresAcciones(), "obs",
                "cont", 5, CLOCK);

        roca.actualizarPlanificacion("Nuevo titulo", null, null, null, null, CLOCK);

        assertThat(roca.titulo()).isEqualTo("Nuevo titulo");
        assertThat(roca.obstaculo()).isEqualTo("obs");
        assertThat(roca.autoevaluacionInicio()).isEqualTo(5);
        assertThat(roca.acciones()).hasSize(3);
    }

    @Test
    void registrarRevisionEsIdempotenteYSobreescribe() {
        RocaSemanal roca = RocaSemanal.planificar(unId(), maestra(), 1, "T", tresAcciones(), null, null, null, CLOCK);

        roca.registrarRevision(8, "bloqueo original", "correccion original", CLOCK);
        roca.registrarRevision(9, "bloqueo nuevo", "correccion nueva", CLOCK);

        assertThat(roca.autoevaluacionFin()).isEqualTo(9);
        assertThat(roca.bloqueoPrincipal()).isEqualTo("bloqueo nuevo");
        assertThat(roca.correccion()).isEqualTo("correccion nueva");
    }

    @Test
    void autoevaluacionFueraDeRangoEsInvalida() {
        RocaSemanal roca = RocaSemanal.planificar(unId(), maestra(), 1, "T", tresAcciones(), null, null, null, CLOCK);
        assertThatThrownBy(() -> roca.registrarRevision(11, "b", "c", CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
