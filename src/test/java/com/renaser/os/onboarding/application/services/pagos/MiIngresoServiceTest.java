package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.MiIngreso;
import com.renaser.os.onboarding.application.ports.in.pagos.MiIngreso.ClaveDePaso;
import com.renaser.os.onboarding.application.ports.in.pagos.MiIngresoUseCase.ConfirmacionPedida;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.CambioDeCuota;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.CuotaPedida;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.MarcaDeCuota;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.PedidoDeCronograma;
import com.renaser.os.onboarding.application.ports.in.pagos.VistaDePagos;
import com.renaser.os.onboarding.domain.model.diagnostico.Diagnostico;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.estado.HitoOnboarding;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeConfirmacion;
import com.renaser.os.onboarding.domain.model.pagos.EstadoDeCuota;
import com.renaser.os.onboarding.domain.model.pagos.ModalidadDePago;
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

/** Yo → «Mi ingreso» (spec 032): lo que ve la persona y su confirmación, con las salidas en memoria. */
class MiIngresoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-03T03:00:00Z"));

    private PagosEnMemoria.Estados estados;
    private PagosEnMemoria.Cuotas cuotas;
    private PagosEnMemoria.Actores actores;
    private PagosEnMemoria.Etapas etapas;
    private PagosDeLaPersonaService admin;
    private MiIngresoService miIngreso;
    private UserId administracion;
    private UserId persona;

    @BeforeEach
    void preparar() {
        estados = new PagosEnMemoria.Estados();
        cuotas = new PagosEnMemoria.Cuotas();
        actores = new PagosEnMemoria.Actores();
        etapas = new PagosEnMemoria.Etapas();
        ArmadorDeVistaDePagos armador = new ArmadorDeVistaDePagos(cuotas, actores);
        admin = new PagosDeLaPersonaService(new GuardiaDePagos(actores), actores, estados, estados, cuotas, armador, new PagosEnMemoria.Ids(), CLOCK);
        miIngreso = new MiIngresoService(estados, estados, actores, etapas, armador, CLOCK);
        administracion = actores.cuenta("Ana Admin", true, false);
        persona = actores.cuenta("Beto Aprendiz", false, false);
    }

    private static PedidoDeCronograma tres() {
        return new PedidoDeCronograma(true, "PEN", List.of(cuota("2026-10-15"), cuota("2026-11-15"), cuota("2026-12-15")));
    }

    private static CuotaPedida cuota(String fecha) {
        return new CuotaPedida(LocalDate.parse(fecha), new BigDecimal("1000"));
    }

    private static List<ClaveDePaso> claves(MiIngreso ingreso) {
        return ingreso.pasos().stream().map(MiIngreso.Paso::clave).toList();
    }

    // ------------------------------------------------------------------------------------------ qué se ve

    @Test
    @DisplayName("sin pago anotado no hay nada que mostrar: ni pasos ni cronograma (la fila de Yo no se dibuja)")
    void sinPagoAnotado() {
        assertThat(miIngreso.ver(persona).pagos()).isNull();
        assertThat(miIngreso.ver(persona).pasos()).isEmpty();

        estados.guardar(EstadoOnboarding.iniciar(persona, CLOCK));
        assertThat(miIngreso.ver(persona).pagos()).as("tener una fila de onboarding no es tener un pago").isNull();
    }

    @Test
    @DisplayName("«Sin cronograma»: el paso de Pagos está hecho («Tu pago fue cancelado») y no hay cuotas ni confirmación")
    void sinCronograma() {
        admin.asignar(administracion, persona, PedidoDeCronograma.sinCronograma());

        MiIngreso ingreso = miIngreso.ver(persona);

        assertThat(ingreso.pagos().modalidad()).isEqualTo(ModalidadDePago.SIN_CRONOGRAMA);
        assertThat(ingreso.pagos().cuotas()).isEmpty();
        assertThat(ingreso.pagos().confirmacion()).isNull();
        assertThat(ingreso.pasos()).containsExactly(new MiIngreso.Paso(ClaveDePaso.ACUERDO, false), new MiIngreso.Paso(ClaveDePaso.PAGOS, true));
    }

    @Test
    @DisplayName("«Con cronograma»: las cuotas en orden, sin el nombre de quien marcó (eso lo ve Administración), y Pagos todavía no está hecho")
    void conCronograma() {
        admin.asignar(administracion, persona, tres());
        VistaDePagos vista = admin.ver(administracion, persona);
        admin.marcarCuota(administracion, persona, new MarcaDeCuota(vista.pago().cuotas().get(0).cuota().id(), EstadoDeCuota.PAGADA));

        MiIngreso ingreso = miIngreso.ver(persona);

        assertThat(ingreso.pagos().cuotas()).hasSize(3);
        assertThat(ingreso.pagos().cuotas().get(0).cuota().pagada()).isTrue();
        assertThat(ingreso.pagos().cuotas()).allSatisfy(c -> assertThat(c.marcadaPor()).isNull());
        assertThat(ingreso.pagos().confirmacion()).isEqualTo(EstadoDeConfirmacion.SIN_CONFIRMAR);
        assertThat(ingreso.pasos()).extracting(MiIngreso.Paso::hecho).containsExactly(false, false);
    }

    @Test
    @DisplayName("el paso de Pagos queda hecho cuando todas las cuotas figuran pagadas")
    void pagosHechos() {
        admin.asignar(administracion, persona, tres());
        admin.ver(administracion, persona).pago().cuotas()
                .forEach(c -> admin.marcarCuota(administracion, persona, new MarcaDeCuota(c.cuota().id(), EstadoDeCuota.PAGADA)));

        assertThat(miIngreso.ver(persona).pasos()).extracting(MiIngreso.Paso::hecho).containsExactly(false, true);
    }

    @Test
    @DisplayName("Diagnóstico solo si le tocó; y Acuerdo se marca con los términos aceptados")
    void pasosSegunLaPersona() {
        admin.asignar(administracion, persona, PedidoDeCronograma.sinCronograma());
        assertThat(claves(miIngreso.ver(persona))).containsExactly(ClaveDePaso.ACUERDO, ClaveDePaso.PAGOS);

        EstadoOnboarding estado = estados.filas.get(persona);
        estado.asignarDiagnostico(administracion, CLOCK);
        assertThat(claves(miIngreso.ver(persona))).containsExactly(ClaveDePaso.DIAGNOSTICO, ClaveDePaso.ACUERDO, ClaveDePaso.PAGOS);
        assertThat(miIngreso.ver(persona).pasos().get(0).hecho()).isFalse();

        etapas.marcarCompletada(persona, Diagnostico.FLUJO);
        estado.aceptarHito(HitoOnboarding.TERMINOS, CLOCK);
        assertThat(miIngreso.ver(persona).pasos()).extracting(MiIngreso.Paso::hecho).containsExactly(true, true, true);
    }

    @Test
    @DisplayName("una cuenta suspendida no ve nada (403) y una que no existe es 404")
    void cuentaSuspendida() {
        UserId suspendida = actores.cuenta("Suspendida", false, true);

        assertThatThrownBy(() -> miIngreso.ver(suspendida)).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> miIngreso.confirmar(suspendida, new ConfirmacionPedida("X", true))).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> miIngreso.ver(UserId.of(UUID.randomUUID()))).isInstanceOf(NoSuchElementException.class);
    }

    // ------------------------------------------------------------------------------------------ confirmar

    @Test
    @DisplayName("confirmar: la casilla es obligatoria, el nombre también, y solo un cronograma se confirma")
    void confirmarExigeLoSuyo() {
        assertThatThrownBy(() -> miIngreso.confirmar(persona, new ConfirmacionPedida("Beto", true))).isInstanceOf(IllegalStateException.class);
        admin.asignar(administracion, persona, PedidoDeCronograma.sinCronograma());
        assertThatThrownBy(() -> miIngreso.confirmar(persona, new ConfirmacionPedida("Beto", true))).isInstanceOf(IllegalStateException.class);

        admin.asignar(administracion, persona, tres());
        assertThatThrownBy(() -> miIngreso.confirmar(persona, new ConfirmacionPedida("Beto", false))).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revisaste");
        assertThatThrownBy(() -> miIngreso.confirmar(persona, new ConfirmacionPedida("  ", true))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> miIngreso.confirmar(persona, new ConfirmacionPedida(null, true))).isInstanceOf(IllegalArgumentException.class);
        assertThat(miIngreso.ver(persona).pagos().confirmacion()).isEqualTo(EstadoDeConfirmacion.SIN_CONFIRMAR);
    }

    @Test
    @DisplayName("confirmar deja su nombre y la hora; no bloquea ni cambia nada más")
    void confirmar() {
        admin.asignar(administracion, persona, tres());

        MiIngreso ingreso = miIngreso.confirmar(persona, new ConfirmacionPedida("  Beto Aprendiz Quispe ", true));

        assertThat(ingreso.pagos().confirmacion()).isEqualTo(EstadoDeConfirmacion.CONFIRMADA);
        assertThat(ingreso.pagos().nombreConfirmado()).isEqualTo("Beto Aprendiz Quispe");
        assertThat(ingreso.pagos().confirmadaEn()).isEqualTo(CLOCK.now());
        assertThat(ingreso.pagos().cuotas()).hasSize(3);
    }

    @Test
    @DisplayName("confirmar dos veces lo mismo no cambia la primera hora ni el primer nombre")
    void confirmarEsIdempotente() {
        admin.asignar(administracion, persona, tres());
        miIngreso.confirmar(persona, new ConfirmacionPedida("Beto", true));
        Instant primera = estados.filas.get(persona).pago().confirmadoEn();

        MiIngreso otra = new MiIngresoService(estados, estados, actores, etapas, new ArmadorDeVistaDePagos(cuotas, actores),
                FixedClock.at(primera.plusSeconds(600))).confirmar(persona, new ConfirmacionPedida("Otro nombre", true));

        assertThat(otra.pagos().confirmadaEn()).isEqualTo(primera);
        assertThat(otra.pagos().nombreConfirmado()).isEqualTo("Beto");
    }

    @Test
    @DisplayName("si Administración cambia las cuotas, la confirmación queda desactualizada y se vuelve a pedir; al confirmar de nuevo vuelve a valer")
    void cambiarLasCuotasPideConfirmarDeNuevo() {
        VistaDePagos vista = admin.asignar(administracion, persona, tres());
        miIngreso.confirmar(persona, new ConfirmacionPedida("Beto", true));
        assertThat(miIngreso.ver(persona).pagos().confirmacion()).isEqualTo(EstadoDeConfirmacion.CONFIRMADA);

        admin.agregarCuota(administracion, persona, cuota("2027-01-15"));
        MiIngreso desactualizada = miIngreso.ver(persona);
        assertThat(desactualizada.pagos().confirmacion()).isEqualTo(EstadoDeConfirmacion.DESACTUALIZADA);
        assertThat(desactualizada.pagos().confirmadaEn()).as("sigue diciendo cuándo confirmó lo anterior").isNotNull();

        MiIngreso otra = miIngreso.confirmar(persona, new ConfirmacionPedida("Beto Aprendiz", true));
        assertThat(otra.pagos().confirmacion()).isEqualTo(EstadoDeConfirmacion.CONFIRMADA);
        assertThat(otra.pagos().nombreConfirmado()).isEqualTo("Beto Aprendiz");

        admin.editarCuota(administracion, persona, new CambioDeCuota(vista.pago().cuotas().get(0).cuota().id(), cuota("2026-10-16")));
        assertThat(miIngreso.ver(persona).pagos().confirmacion()).isEqualTo(EstadoDeConfirmacion.DESACTUALIZADA);
    }

    @Test
    @DisplayName("marcar un pago no pide confirmar de nuevo")
    void marcarUnPagoNoDesactualiza() {
        VistaDePagos vista = admin.asignar(administracion, persona, tres());
        miIngreso.confirmar(persona, new ConfirmacionPedida("Beto", true));

        admin.marcarCuota(administracion, persona, new MarcaDeCuota(vista.pago().cuotas().get(0).cuota().id(), EstadoDeCuota.PAGADA));

        assertThat(miIngreso.ver(persona).pagos().confirmacion()).isEqualTo(EstadoDeConfirmacion.CONFIRMADA);
    }
}
