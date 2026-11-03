package com.renaser.os.onboarding.domain.model.pagos;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CronogramaDePagosTest {

    private static final Instant CREADA = Instant.parse("2026-11-01T15:00:00Z");
    private static final Instant MARCADA = Instant.parse("2026-11-20T15:30:00Z");
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());

    private static Cuota cuota(String fecha, String monto) {
        return Cuota.nueva(UUID.randomUUID(), LocalDate.parse(fecha), new BigDecimal(monto), CREADA);
    }

    private static CronogramaDePagos tresCuotas() {
        return CronogramaDePagos.nuevo(Moneda.USD, List.of(cuota("2026-12-15", "1000"), cuota("2026-10-15", "1000"), cuota("2026-11-15", "1000")));
    }

    // decisión 2026-11-02 pagos fuera de términos
    @Test
    @DisplayName("lleva de 0 a 60 cuotas: puede nacer sin cuotas (Administración las registra después) pero 61 es un error de quien lo arma (400)")
    void deCeroASesenta() {
        assertThat(CronogramaDePagos.nuevo(Moneda.PEN, List.of()).cuotas()).isEmpty();
        assertThat(CronogramaDePagos.nuevo(Moneda.PEN, null).cuotas()).isEmpty();
        List<Cuota> sesentaYUna = new ArrayList<>();
        for (int i = 0; i < 61; i++) {
            sesentaYUna.add(cuota("2026-11-15", "10"));
        }
        assertThatThrownBy(() -> CronogramaDePagos.nuevo(Moneda.PEN, sesentaYUna)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("hasta 60");
        assertThat(CronogramaDePagos.nuevo(Moneda.PEN, sesentaYUna.subList(0, 60)).cuotas()).hasSize(60);
    }

    // decisión 2026-11-02 pagos fuera de términos
    @Test
    @DisplayName("un cronograma sin cuotas todavía acepta agregar la primera, y una vez que tiene cuotas la última no se quita")
    void sinCuotasSePuedeLlenarDespues() {
        CronogramaDePagos vacio = CronogramaDePagos.nuevo(Moneda.USD, List.of());
        assertThat(vacio.hayPagadas()).isFalse();
        assertThat(vacio.todasPagadas()).as("sin cuotas no hay nada pagado").isFalse();

        Cuota primera = vacio.agregar(cuota("2026-11-15", "300"));

        assertThat(vacio.cuotas()).containsExactly(primera);
        assertThatThrownBy(() -> vacio.quitar(primera.id())).isInstanceOf(IllegalStateException.class).hasMessageContaining("al menos una cuota");
    }

    @Test
    @DisplayName("la moneda es obligatoria: no hay valor por defecto")
    void laMonedaEsObligatoria() {
        assertThatThrownBy(() -> CronogramaDePagos.nuevo(null, List.of(cuota("2026-11-15", "10")))).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Moneda.desde(null)).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Elige la moneda");
        assertThatThrownBy(() -> Moneda.desde("EUR")).isInstanceOf(IllegalArgumentException.class);
        assertThat(Moneda.desde("pen")).isEqualTo(Moneda.PEN);
        assertThat(Moneda.PEN.simbolo()).isEqualTo("S/");
        assertThat(Moneda.USD.simbolo()).isEqualTo("US$");
    }

    @Test
    @DisplayName("las cuotas salen de la fecha más cercana a la más lejana, aunque se hayan armado en otro orden")
    void enOrdenDeFecha() {
        assertThat(tresCuotas().cuotas()).extracting(Cuota::venceEl)
                .containsExactly(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 15));
    }

    @Test
    @DisplayName("agregar suma una cuota pendiente en su lugar; la 61 es un 409")
    void agregar() {
        CronogramaDePagos cronograma = tresCuotas();

        cronograma.agregar(cuota("2026-11-01", "500"));

        assertThat(cronograma.cuotas()).extracting(Cuota::venceEl).first().isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(cronograma.cuotas()).hasSize(4).extracting(Cuota::venceEl).containsSubsequence(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 15));

        List<Cuota> sesenta = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            sesenta.add(cuota("2026-11-15", "10"));
        }
        CronogramaDePagos lleno = CronogramaDePagos.nuevo(Moneda.PEN, sesenta);
        assertThatThrownBy(() -> lleno.agregar(cuota("2026-11-16", "10"))).isInstanceOf(IllegalStateException.class).hasMessageContaining("60");
    }

    @Test
    @DisplayName("editar cambia la fecha y el monto de una pendiente y la deja en su lugar nuevo; una pagada no se edita (409); una ajena es 404")
    void editar() {
        CronogramaDePagos cronograma = tresCuotas();
        Cuota primera = cronograma.cuotas().get(0);
        Cuota segunda = cronograma.cuotas().get(1);

        Cuota editada = cronograma.editar(primera.id(), LocalDate.of(2027, 1, 10), new BigDecimal("750.5"));

        assertThat(editada.monto()).isEqualTo(new BigDecimal("750.50"));
        assertThat(cronograma.cuotas()).extracting(Cuota::id).last().isEqualTo(primera.id());

        cronograma.marcar(segunda.id(), EstadoDeCuota.PAGADA, ADMIN, MARCADA);
        assertThatThrownBy(() -> cronograma.editar(segunda.id(), LocalDate.of(2027, 2, 1), BigDecimal.TEN)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cronograma.editar(UUID.randomUUID(), LocalDate.of(2027, 2, 1), BigDecimal.TEN)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("quitar saca una pendiente; no una pagada, no la última y no una que no es de este cronograma")
    void quitar() {
        CronogramaDePagos cronograma = tresCuotas();
        Cuota primera = cronograma.cuotas().get(0);
        Cuota segunda = cronograma.cuotas().get(1);
        Cuota tercera = cronograma.cuotas().get(2);

        assertThat(cronograma.quitar(primera.id()).id()).isEqualTo(primera.id());
        assertThat(cronograma.cuotas()).hasSize(2);

        cronograma.marcar(segunda.id(), EstadoDeCuota.PAGADA, ADMIN, MARCADA);
        assertThatThrownBy(() -> cronograma.quitar(segunda.id())).isInstanceOf(IllegalStateException.class).hasMessageContaining("pagada");
        assertThatThrownBy(() -> cronograma.quitar(UUID.randomUUID())).isInstanceOf(NoSuchElementException.class);

        cronograma.quitar(tercera.id());
        assertThatThrownBy(() -> cronograma.quitar(segunda.id())).isInstanceOf(IllegalStateException.class);

        CronogramaDePagos deUna = CronogramaDePagos.nuevo(Moneda.PEN, List.of(cuota("2026-11-15", "10")));
        assertThatThrownBy(() -> deUna.quitar(deUna.cuotas().get(0).id())).isInstanceOf(IllegalStateException.class).hasMessageContaining("Sin cronograma");
    }

    @Test
    @DisplayName("marcar pagada y devolver a pendiente; hayPagadas y todasPagadas lo reflejan")
    void marcar() {
        CronogramaDePagos cronograma = tresCuotas();
        assertThat(cronograma.hayPagadas()).isFalse();
        assertThat(cronograma.todasPagadas()).isFalse();

        cronograma.cuotas().forEach(c -> cronograma.marcar(c.id(), EstadoDeCuota.PAGADA, ADMIN, MARCADA));
        assertThat(cronograma.hayPagadas()).isTrue();
        assertThat(cronograma.todasPagadas()).isTrue();

        cronograma.marcar(cronograma.cuotas().get(1).id(), EstadoDeCuota.PENDIENTE, ADMIN, MARCADA.plusSeconds(5));
        assertThat(cronograma.todasPagadas()).isFalse();
        assertThat(cronograma.cuotas().get(1).pagada()).isFalse();
        assertThatThrownBy(() -> cronograma.marcar(UUID.randomUUID(), EstadoDeCuota.PAGADA, ADMIN, MARCADA)).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("la huella es la misma para las mismas fechas y montos, sin importar el orden en que se armaron ni quién las pagó")
    void huellaEstable() {
        Cuota a = cuota("2026-11-15", "1000");
        Cuota b = cuota("2026-12-15", "1000");
        CronogramaDePagos uno = CronogramaDePagos.nuevo(Moneda.USD, List.of(a, b));
        CronogramaDePagos otro = CronogramaDePagos.nuevo(Moneda.USD,
                List.of(cuota("2026-12-15", "1000.00"), cuota("2026-11-15", "1000")));

        assertThat(uno.huella()).hasSize(64).isEqualTo(otro.huella());

        uno.marcar(a.id(), EstadoDeCuota.PAGADA, ADMIN, MARCADA);
        assertThat(uno.huella()).as("marcar un pago no cambia lo acordado").isEqualTo(otro.huella());
    }

    @Test
    @DisplayName("la huella cambia en cuanto cambia lo acordado: la moneda, una fecha, un monto, una cuota de más o de menos")
    void huellaCambia() {
        Cuota a = cuota("2026-11-15", "1000");
        Cuota b = cuota("2026-12-15", "1000");
        String base = CronogramaDePagos.nuevo(Moneda.USD, List.of(a, b)).huella();

        assertThat(CronogramaDePagos.nuevo(Moneda.PEN, List.of(a, b)).huella()).isNotEqualTo(base);

        CronogramaDePagos editada = CronogramaDePagos.nuevo(Moneda.USD, List.of(a, b));
        editada.editar(b.id(), LocalDate.of(2026, 12, 16), new BigDecimal("1000"));
        assertThat(editada.huella()).isNotEqualTo(base);
        editada.editar(b.id(), LocalDate.of(2026, 12, 15), new BigDecimal("1001"));
        assertThat(editada.huella()).isNotEqualTo(base);
        editada.editar(b.id(), LocalDate.of(2026, 12, 15), new BigDecimal("1000"));
        assertThat(editada.huella()).as("si se deja como estaba, vuelve a ser la misma").isEqualTo(base);

        CronogramaDePagos conOtra = CronogramaDePagos.nuevo(Moneda.USD, List.of(a, b));
        conOtra.agregar(cuota("2027-01-15", "1000"));
        assertThat(conOtra.huella()).isNotEqualTo(base);
        conOtra.quitar(conOtra.cuotas().get(2).id());
        assertThat(conOtra.huella()).isEqualTo(base);
    }
}
