package com.renaser.os.rocks.domain.model.rocasemanal;

import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.shared.domain.FixedClock;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * > <b>Corregido el 2026-09-22.</b> Esta clase tenia tres tests sobre la lista de acciones de la
 * > semana —{@code aceptaMenosDeTresAccionesYHastaNinguna}, {@code rechazaMasDeTresAcciones} y
 * > {@code rechazaOrdenesRepetidos}—. No se perdio cobertura al sacarlos: esas invariantes (hasta
 * > tres, sin huecos, con texto) bajaron al objetivo diario junto con las acciones y las cubre
 * > {@code AccionDiariaTest}. La semana ya no tiene acciones; tiene un objetivo.
 */
class RocaSemanalTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));

    private static RocaMaestraId maestra() {
        return RocaMaestraId.of(UUID.randomUUID());
    }

    /** El id ya no lo sortea la factoria: entra por parametro (puerto IdGenerator). */
    private static RocaSemanalId unId() {
        return RocaSemanalId.of(UUID.randomUUID());
    }

    @Test
    void planificarCreaLaRocaAbiertaSinRevision() {
        RocaSemanalId id = unId();
        RocaSemanal roca = RocaSemanal.planificar(id, maestra(), 3, "Titulo", "obstaculo",
                "contingencia", 7, CLOCK);

        assertThat(roca.id()).isEqualTo(id);
        assertThat(roca.numeroSemana()).isEqualTo(3);
        assertThat(roca.titulo()).isEqualTo("Titulo");
        assertThat(roca.autoevaluacionFin()).isNull();
        assertThat(roca.bloqueoPrincipal()).isNull();
        assertThat(roca.creadoEn()).isEqualTo(CLOCK.now());
    }

    @Test
    void numeroSemanaFueraDeRangoEsInvalido() {
        assertThatThrownBy(() -> RocaSemanal.planificar(unId(), maestra(), 14, "T", null, null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RocaSemanal.planificar(unId(), maestra(), 0, "T", null, null, null, CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void actualizarPlanificacionSoloTocaLosCamposNoNulos() {
        RocaSemanal roca = RocaSemanal.planificar(unId(), maestra(), 1, "Original", "obs", "cont", 5, CLOCK);

        roca.actualizarPlanificacion("Nuevo titulo", null, null, null, CLOCK);

        assertThat(roca.titulo()).isEqualTo("Nuevo titulo");
        assertThat(roca.obstaculo()).isEqualTo("obs");
        assertThat(roca.contingencia()).isEqualTo("cont");
        assertThat(roca.autoevaluacionInicio()).isEqualTo(5);
    }

    @Test
    void registrarRevisionEsIdempotenteYSobreescribe() {
        RocaSemanal roca = RocaSemanal.planificar(unId(), maestra(), 1, "T", null, null, null, CLOCK);

        roca.registrarRevision(8, "bloqueo original", "correccion original", CLOCK);
        roca.registrarRevision(9, "bloqueo nuevo", "correccion nueva", CLOCK);

        assertThat(roca.autoevaluacionFin()).isEqualTo(9);
        assertThat(roca.bloqueoPrincipal()).isEqualTo("bloqueo nuevo");
        assertThat(roca.correccion()).isEqualTo("correccion nueva");
    }

    @Test
    void autoevaluacionFueraDeRangoEsInvalida() {
        RocaSemanal roca = RocaSemanal.planificar(unId(), maestra(), 1, "T", null, null, null, CLOCK);
        assertThatThrownBy(() -> roca.registrarRevision(11, "b", "c", CLOCK))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
