package com.renaser.os.onboarding.application.services.pagos;

import com.renaser.os.onboarding.application.ports.in.pagos.CronogramaParaFirmarUseCase.CronogramaParaFirmar;
import com.renaser.os.onboarding.application.ports.in.pagos.MiIngresoUseCase.ConfirmacionPedida;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.CambioDeCuota;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.CuotaPedida;
import com.renaser.os.onboarding.application.ports.in.pagos.PagosDeLaPersonaUseCase.PedidoDeCronograma;
import com.renaser.os.onboarding.domain.model.estado.EstadoOnboarding;
import com.renaser.os.onboarding.domain.model.pagos.Moneda;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** El cronograma que se ofrece para firmar con los términos (spec 039): el mismo de «Mi ingreso» (spec 032) y con la misma huella. */
class CronogramaParaFirmarServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-03T03:00:00Z"));

    private PagosEnMemoria.Estados estados;
    private PagosDeLaPersonaService admin;
    private MiIngresoService miIngreso;
    private CronogramaParaFirmarService paraFirmar;
    private UserId administracion;
    private UserId persona;

    @BeforeEach
    void preparar() {
        estados = new PagosEnMemoria.Estados();
        PagosEnMemoria.Cuotas cuotas = new PagosEnMemoria.Cuotas();
        PagosEnMemoria.Actores actores = new PagosEnMemoria.Actores();
        ArmadorDeVistaDePagos armador = new ArmadorDeVistaDePagos(cuotas, actores);
        admin = new PagosDeLaPersonaService(new GuardiaDePagos(actores), actores, estados, estados, cuotas, armador, new PagosEnMemoria.Ids(), CLOCK);
        miIngreso = new MiIngresoService(estados, estados, actores, new PagosEnMemoria.Etapas(), armador, CLOCK);
        paraFirmar = new CronogramaParaFirmarService(estados, armador);
        administracion = actores.cuenta("Ana Admin", true, false);
        persona = actores.cuenta("Beto Aprendiz", false, false);
    }

    private static PedidoDeCronograma dosCuotas() {
        return new PedidoDeCronograma(true, "PEN", List.of(cuota("2026-11-15", "500"), cuota("2026-12-15", "500.50")));
    }

    private static CuotaPedida cuota(String fecha, String monto) {
        return new CuotaPedida(LocalDate.parse(fecha), new BigDecimal(monto));
    }

    @Test
    @DisplayName("sin pago anotado, sin fila o «pagó completo» no hay cronograma que firmar")
    void sinCronograma() {
        assertThat(paraFirmar.de(persona)).as("sin fila de ingreso").isEmpty();

        estados.guardar(EstadoOnboarding.iniciar(persona, CLOCK));
        assertThat(paraFirmar.de(persona)).as("sin pago anotado").isEmpty();

        admin.asignar(administracion, persona, PedidoDeCronograma.sinCronograma());
        assertThat(paraFirmar.de(persona)).as("pagó completo").isEmpty();
    }

    @Test
    @DisplayName("con cronograma: la moneda, las cuotas en orden y la huella; sin confirmar todavía")
    void conCronograma() {
        admin.asignar(administracion, persona, dosCuotas());

        CronogramaParaFirmar cronograma = paraFirmar.de(persona).orElseThrow();

        assertThat(cronograma.moneda()).isEqualTo(Moneda.PEN);
        assertThat(cronograma.cuotas()).extracting(c -> c.venceEl().toString()).containsExactly("2026-11-15", "2026-12-15");
        assertThat(cronograma.huella()).isNotBlank();
        assertThat(cronograma.yaConfirmado()).isFalse();
    }

    @Test
    @DisplayName("la huella es la MISMA que guarda «Mi ingreso» al confirmar: confirmar allá lo deja confirmado acá")
    void laMismaHuellaQueMiIngreso() {
        admin.asignar(administracion, persona, dosCuotas());
        String huella = paraFirmar.de(persona).orElseThrow().huella();

        miIngreso.confirmar(persona, new ConfirmacionPedida("Beto Aprendiz", true));

        assertThat(estados.filas.get(persona).pago().confirmadoHuella()).isEqualTo(huella);
        assertThat(paraFirmar.de(persona).orElseThrow().yaConfirmado()).isTrue();
        assertThat(paraFirmar.de(persona).orElseThrow().huella()).isEqualTo(huella);
    }

    @Test
    @DisplayName("si Administración cambia una fecha o un monto después de confirmar, la huella cambia y vuelve a quedar sin confirmar")
    void cambiaLaHuella() {
        admin.asignar(administracion, persona, dosCuotas());
        String antes = paraFirmar.de(persona).orElseThrow().huella();
        miIngreso.confirmar(persona, new ConfirmacionPedida("Beto Aprendiz", true));
        var cuotaAEditar = admin.ver(administracion, persona).pago().cuotas().get(1).cuota();

        admin.editarCuota(administracion, persona, new CambioDeCuota(cuotaAEditar.id(), cuota("2026-12-20", "500.50")));

        Optional<CronogramaParaFirmar> despues = paraFirmar.de(persona);
        assertThat(despues.orElseThrow().huella()).isNotEqualTo(antes);
        assertThat(despues.orElseThrow().yaConfirmado()).isFalse();
    }
}
