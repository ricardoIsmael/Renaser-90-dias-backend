package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.CambioDeCuota;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.CuotaPedida;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.MarcaDeCuota;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.PedidoDeCronograma;
import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos;
import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos.CuotaVista;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeConfirmacion;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeCuota;
import com.renaser.os.onboarding.domain.model.pagos.ModalidadDePago;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** El caso de uso de Administración sobre los pagos de una persona (spec 032), con las salidas en memoria. */
class PagosDeLaPersonaServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-03T03:00:00Z"));

    private PagosEnMemoria.Estados estados;
    private PagosEnMemoria.Cuotas cuotas;
    private PagosEnMemoria.Actores actores;
    private PagosDeLaPersonaService servicio;
    private UserId admin;
    private UserId persona;

    @BeforeEach
    void preparar() {
        estados = new PagosEnMemoria.Estados();
        cuotas = new PagosEnMemoria.Cuotas();
        actores = new PagosEnMemoria.Actores();
        servicio = new PagosDeLaPersonaService(new GuardiaDePagos(actores), actores, estados, estados, cuotas, new ArmadorDeVistaDePagos(cuotas, actores),
                new PagosEnMemoria.Ids(), CLOCK);
        admin = actores.cuenta("Ana Admin", true, false);
        persona = actores.cuenta("Beto Aprendiz", false, false);
    }

    private static PedidoDeCronograma tresCuotasMensuales() {
        return new PedidoDeCronograma(true, "USD", List.of(cuota("2026-10-15", "1000"), cuota("2026-11-15", "1000"), cuota("2026-12-15", "1000")));
    }

    private static CuotaPedida cuota(String fecha, String monto) {
        return new CuotaPedida(LocalDate.parse(fecha), new BigDecimal(monto));
    }

    private UUID idDeLaCuota(VistaDePagos vista, int lugar) {
        return vista.pago().cuotas().get(lugar).cuota().id();
    }

    // ------------------------------------------------------------------------------------------ quién puede

    @Test
    @DisplayName("solo Administración y Alquimista: cualquier otro rol es 403 y no toca nada")
    void soloAdministracion() {
        UserId mentor = actores.cuenta("Mentor", false, false);
        UserId suspendida = actores.cuenta("Admin suspendida", true, true);

        for (UserId actor : List.of(mentor, persona, suspendida)) {
            assertThatThrownBy(() -> servicio.ver(actor, persona)).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> servicio.asignar(actor, persona, tresCuotasMensuales())).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> servicio.agregarCuota(actor, persona, cuota("2026-12-01", "10"))).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> servicio.quitarCuota(actor, persona, UUID.randomUUID())).isInstanceOf(NotAuthorizedException.class);
            assertThatThrownBy(() -> servicio.marcarCuota(actor, persona, new MarcaDeCuota(UUID.randomUUID(), EstadoDeCuota.PAGADA))).isInstanceOf(NotAuthorizedException.class);
        }
        assertThat(estados.filas).isEmpty();
        assertThat(cuotas.escrituras).isZero();
    }

    @Test
    @DisplayName("una persona que no existe es 404")
    void personaInexistente() {
        UserId fantasma = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> servicio.ver(admin, fantasma)).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.asignar(admin, fantasma, PedidoDeCronograma.sinCronograma())).isInstanceOf(NoSuchElementException.class);
    }

    // ------------------------------------------------------------------------------------------ asignar

    @Test
    @DisplayName("sin nada anotado: el pago es nulo (la app no muestra «Mi ingreso») y ver no crea ninguna fila")
    void sinAnotar() {
        VistaDePagos vista = servicio.ver(admin, persona);

        assertThat(vista.nombreDeLaPersona()).isEqualTo("Beto Aprendiz");
        assertThat(vista.pago()).isNull();
        assertThat(estados.filas).isEmpty();
    }

    @Test
    @DisplayName("«Sin cronograma» (pagó completo): anota la modalidad, sin cuotas ni moneda")
    void sinCronograma() {
        VistaDePagos vista = servicio.asignar(admin, persona, PedidoDeCronograma.sinCronograma());

        assertThat(vista.pago().modalidad()).isEqualTo(ModalidadDePago.SIN_CRONOGRAMA);
        assertThat(vista.pago().cuotas()).isEmpty();
        assertThat(vista.pago().moneda()).isNull();
        assertThat(vista.pago().confirmacion()).isNull();
        assertThat(estados.filas.get(persona).iniciadoEn()).as("la fila nace antes de que la persona empiece").isNull();
    }

    @Test
    @DisplayName("«Con cronograma»: guarda las cuotas pendientes en orden, con su moneda, sin confirmar")
    void conCronograma() {
        VistaDePagos vista = servicio.asignar(admin, persona, tresCuotasMensuales());

        assertThat(vista.pago().modalidad()).isEqualTo(ModalidadDePago.CON_CRONOGRAMA);
        assertThat(vista.pago().moneda()).isEqualTo(Moneda.USD);
        assertThat(vista.pago().cuotas()).extracting(c -> c.cuota().venceEl())
                .containsExactly(LocalDate.of(2026, 10, 15), LocalDate.of(2026, 11, 15), LocalDate.of(2026, 12, 15));
        assertThat(vista.pago().cuotas()).allSatisfy(c -> assertThat(c.cuota().pagada()).isFalse());
        assertThat(vista.pago().confirmacion()).isEqualTo(EstadoDeConfirmacion.SIN_CONFIRMAR);
    }

    @Test
    @DisplayName("un cronograma inválido (sin moneda, sin cuotas, monto cero, una cuota nula) es 400 y no deja nada a medias ni borra el anterior")
    void invalidoNoDejaNadaAMedias() {
        servicio.asignar(admin, persona, tresCuotasMensuales());
        int escrituras = cuotas.escrituras;

        List<PedidoDeCronograma> invalidos = List.of(
                new PedidoDeCronograma(true, null, List.of(cuota("2026-11-15", "10"))),
                new PedidoDeCronograma(true, "EUR", List.of(cuota("2026-11-15", "10"))),
                new PedidoDeCronograma(true, "PEN", List.of()),
                new PedidoDeCronograma(true, "PEN", null),
                new PedidoDeCronograma(true, "PEN", List.of(cuota("2026-11-15", "0"))),
                new PedidoDeCronograma(true, "PEN", java.util.Arrays.asList(cuota("2026-11-15", "10"), null)));
        for (PedidoDeCronograma invalido : invalidos) {
            assertThatThrownBy(() -> servicio.asignar(admin, persona, invalido)).as("%s", invalido).isInstanceOf(IllegalArgumentException.class);
        }

        assertThat(cuotas.escrituras).as("ninguna escritura después de las validaciones").isEqualTo(escrituras);
        assertThat(servicio.ver(admin, persona).pago().cuotas()).hasSize(3);
    }

    @Test
    @DisplayName("se puede reemplazar el cronograma mientras no haya cuotas pagadas; con una pagada es 409 y no se toca nada")
    void reemplazarSoloSinPagadas() {
        VistaDePagos vista = servicio.asignar(admin, persona, tresCuotasMensuales());

        VistaDePagos otro = servicio.asignar(admin, persona, new PedidoDeCronograma(true, "PEN", List.of(cuota("2027-01-10", "500"))));
        assertThat(otro.pago().moneda()).isEqualTo(Moneda.PEN);
        assertThat(otro.pago().cuotas()).hasSize(1);

        VistaDePagos conPagada = servicio.asignar(admin, persona, tresCuotasMensuales());
        servicio.marcarCuota(admin, persona, new MarcaDeCuota(idDeLaCuota(conPagada, 0), EstadoDeCuota.PAGADA));
        assertThatThrownBy(() -> servicio.asignar(admin, persona, PedidoDeCronograma.sinCronograma())).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cuotas pagadas");
        assertThatThrownBy(() -> servicio.asignar(admin, persona, tresCuotasMensuales())).isInstanceOf(IllegalStateException.class);
        assertThat(servicio.ver(admin, persona).pago().cuotas()).hasSize(3);
        assertThat(vista.pago().cuotas()).hasSize(3);
    }

    @Test
    @DisplayName("pasar de «con cronograma» sin pagos a «sin cronograma» borra las cuotas y la moneda")
    void aSinCronograma() {
        servicio.asignar(admin, persona, tresCuotasMensuales());

        VistaDePagos vista = servicio.asignar(admin, persona, PedidoDeCronograma.sinCronograma());

        assertThat(vista.pago().modalidad()).isEqualTo(ModalidadDePago.SIN_CRONOGRAMA);
        assertThat(cuotas.deLaPersona(persona)).isEmpty();
    }

    @Test
    @DisplayName("la confirmación de la persona se pierde si se reemplaza el cronograma entero")
    void reasignarBorraLaConfirmacion() {
        servicio.asignar(admin, persona, tresCuotasMensuales());
        estados.filas.get(persona).confirmarCronograma("Beto", "x", CLOCK);

        servicio.asignar(admin, persona, tresCuotasMensuales());

        assertThat(estados.filas.get(persona).pago().confirmadoEn()).isNull();
    }

    // ------------------------------------------------------------------------------------------ cuotas

    @Test
    @DisplayName("agregar, editar y quitar cuotas pendientes cambian el cronograma; sin cronograma asignado es 409")
    void ajustarPendientes() {
        assertThatThrownBy(() -> servicio.agregarCuota(admin, persona, cuota("2026-12-20", "10"))).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("asígnaselo primero");
        servicio.asignar(admin, persona, PedidoDeCronograma.sinCronograma());
        assertThatThrownBy(() -> servicio.agregarCuota(admin, persona, cuota("2026-12-20", "10"))).isInstanceOf(IllegalStateException.class);

        VistaDePagos vista = servicio.asignar(admin, persona, tresCuotasMensuales());
        VistaDePagos conOtra = servicio.agregarCuota(admin, persona, cuota("2027-01-15", "1000"));
        assertThat(conOtra.pago().cuotas()).hasSize(4);

        VistaDePagos editada = servicio.editarCuota(admin, persona, new CambioDeCuota(idDeLaCuota(vista, 1), cuota("2026-11-20", "1100")));
        assertThat(editada.pago().cuotas()).extracting(c -> c.cuota().monto()).contains(new BigDecimal("1100.00"));

        VistaDePagos menos = servicio.quitarCuota(admin, persona, idDeLaCuota(vista, 0));
        assertThat(menos.pago().cuotas()).hasSize(3);
        assertThat(cuotas.deLaPersona(persona)).hasSize(3);
    }

    @Test
    @DisplayName("lo pagado no se toca: editar o quitar una pagada es 409; la última cuota no se quita; una cuota ajena es 404")
    void loPagadoNoSeToca() {
        VistaDePagos vista = servicio.asignar(admin, persona, tresCuotasMensuales());
        UUID pagada = idDeLaCuota(vista, 0);
        servicio.marcarCuota(admin, persona, new MarcaDeCuota(pagada, EstadoDeCuota.PAGADA));

        assertThatThrownBy(() -> servicio.editarCuota(admin, persona, new CambioDeCuota(pagada, cuota("2026-11-01", "5")))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> servicio.quitarCuota(admin, persona, pagada)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> servicio.quitarCuota(admin, persona, UUID.randomUUID())).isInstanceOf(NoSuchElementException.class);

        servicio.quitarCuota(admin, persona, idDeLaCuota(vista, 1));
        servicio.quitarCuota(admin, persona, idDeLaCuota(vista, 2));
        assertThat(cuotas.deLaPersona(persona)).hasSize(1);
    }

    @Test
    @DisplayName("la cuota de otra persona no se puede tocar con su id: es 404")
    void cuotaDeOtraPersona() {
        UserId otra = actores.cuenta("Otra", false, false);
        VistaDePagos deLaOtra = servicio.asignar(admin, otra, tresCuotasMensuales());
        servicio.asignar(admin, persona, tresCuotasMensuales());
        UUID ajena = idDeLaCuota(deLaOtra, 0);

        assertThatThrownBy(() -> servicio.marcarCuota(admin, persona, new MarcaDeCuota(ajena, EstadoDeCuota.PAGADA))).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.editarCuota(admin, persona, new CambioDeCuota(ajena, cuota("2026-11-01", "5")))).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> servicio.quitarCuota(admin, persona, ajena)).isInstanceOf(NoSuchElementException.class);
        assertThat(cuotas.deLaPersona(otra)).hasSize(3);
    }

    // ------------------------------------------------------------------------------------------ marcar

    @Test
    @DisplayName("marcar pagada deja quién y cuándo (con el nombre de quien marcó); devolver a pendiente también")
    void marcarYDevolver() {
        VistaDePagos vista = servicio.asignar(admin, persona, tresCuotasMensuales());
        UUID cuota = idDeLaCuota(vista, 0);

        CuotaVista pagada = servicio.marcarCuota(admin, persona, new MarcaDeCuota(cuota, EstadoDeCuota.PAGADA)).pago().cuotas().get(0);
        assertThat(pagada.cuota().pagada()).isTrue();
        assertThat(pagada.cuota().estadoCambiadoPor()).isEqualTo(admin);
        assertThat(pagada.cuota().estadoCambiadoEn()).isEqualTo(CLOCK.now());
        assertThat(pagada.marcadaPor()).isEqualTo("Ana Admin");

        CuotaVista devuelta = servicio.marcarCuota(admin, persona, new MarcaDeCuota(cuota, EstadoDeCuota.PENDIENTE)).pago().cuotas().get(0);
        assertThat(devuelta.cuota().pagada()).isFalse();
        assertThat(devuelta.marcadaPor()).isEqualTo("Ana Admin");
    }

    @Test
    @DisplayName("un doble toque en «Pagado» deja una sola marca: la segunda no cambia quién ni cuándo")
    void dobleToque() {
        VistaDePagos vista = servicio.asignar(admin, persona, tresCuotasMensuales());
        UUID cuota = idDeLaCuota(vista, 0);
        UserId otroAdmin = actores.cuenta("Otro Admin", true, false);

        servicio.marcarCuota(admin, persona, new MarcaDeCuota(cuota, EstadoDeCuota.PAGADA));
        CuotaVista segunda = servicio.marcarCuota(otroAdmin, persona, new MarcaDeCuota(cuota, EstadoDeCuota.PAGADA)).pago().cuotas().get(0);

        assertThat(segunda.cuota().estadoCambiadoPor()).isEqualTo(admin);
        assertThat(segunda.marcadaPor()).isEqualTo("Ana Admin");
    }

    @Test
    @DisplayName("la confirmación sigue vigente al marcar un pago y se desactualiza al cambiar una fecha o un monto")
    void laConfirmacionSigueLaHuella() {
        VistaDePagos vista = servicio.asignar(admin, persona, tresCuotasMensuales());
        EstadoOnboarding estado = estados.filas.get(persona);
        String huella = new ArmadorDeVistaDePagos(cuotas, actores).cronogramaDe(persona, estado.pago()).huella();
        estado.confirmarCronograma("Beto Aprendiz", huella, CLOCK);

        assertThat(servicio.ver(admin, persona).pago().confirmacion()).isEqualTo(EstadoDeConfirmacion.CONFIRMADA);

        servicio.marcarCuota(admin, persona, new MarcaDeCuota(idDeLaCuota(vista, 0), EstadoDeCuota.PAGADA));
        assertThat(servicio.ver(admin, persona).pago().confirmacion()).as("marcar un pago no la invalida").isEqualTo(EstadoDeConfirmacion.CONFIRMADA);

        servicio.editarCuota(admin, persona, new CambioDeCuota(idDeLaCuota(vista, 1), cuota("2026-11-16", "1000")));
        assertThat(servicio.ver(admin, persona).pago().confirmacion()).isEqualTo(EstadoDeConfirmacion.DESACTUALIZADA);
        assertThat(servicio.ver(admin, persona).pago().nombreConfirmado()).isEqualTo("Beto Aprendiz");
    }
}
