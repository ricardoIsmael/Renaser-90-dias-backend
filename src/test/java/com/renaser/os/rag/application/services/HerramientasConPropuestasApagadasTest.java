package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.services.circuito.CircuitoDePrueba;
import com.renaser.os.rag.application.services.herramientas.HerramientaAgente;
import com.renaser.os.rag.application.services.herramientas.PropuestaDeMarcarHabito;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Spec 002 (FR-015, D-267): con {@code ia.propuestas} apagada para la persona, SER no propone. Al modelo no se le
 * ofrecen las herramientas que dejan una propuesta o una tarjeta ({@code proponer_*}, y {@code marcar_habito_completado}
 * cuando propone), y si igual pide una —un turno que empezó antes— recibe que están en pausa sin que se cree nada. Las
 * de consulta siguen.
 */
class HerramientasConPropuestasApagadasTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private final ConsultarAgendaHabitosPort agenda = mock(ConsultarAgendaHabitosPort.class);
    private final ProponerAccionUseCase proponerAccion = mock(ProponerAccionUseCase.class);
    private final HerramientaQueCuenta propone = new HerramientaQueCuenta("proponer_algo_de_prueba");
    private final HerramientaQueCuenta consulta = new HerramientaQueCuenta("consultar_algo_de_prueba");

    private HerramientasAgenteService servicio(String... apagadas) {
        return new HerramientasAgenteService(agenda, List.of(propone, consulta),
                new PropuestaDeMarcarHabito(agenda, proponerAccion, true),
                FixedClock.at(Instant.parse("2026-10-13T03:00:00Z")), mock(GestionarPlanDeHabitosPort.class),
                CircuitoDePrueba.cerrado().interruptoresConApagadas(apagadas));
    }

    private static List<String> nombres(List<DefinicionHerramienta> definiciones) {
        return definiciones.stream().map(DefinicionHerramienta::nombre).toList();
    }

    @Test
    @DisplayName("apagadas: no se ofrecen las que proponen; las de consulta sí")
    void noSeOfrecen() {
        List<String> ofrecidas = nombres(servicio("ia.propuestas").disponiblesPara(AgenteConversacional.COMPANION,
                APRENDIZ));

        assertThat(ofrecidas).doesNotContain("proponer_algo_de_prueba", CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO)
                .contains("consultar_algo_de_prueba", CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA);
    }

    @Test
    @DisplayName("prendidas (o con «ia» prendida y nada más apagado): se ofrecen todas, igual que disponibles()")
    void prendidasSeOfrecenTodas() {
        HerramientasAgenteService servicio = servicio();

        assertThat(servicio.disponiblesPara(AgenteConversacional.COMPANION, APRENDIZ))
                .isEqualTo(servicio.disponibles(AgenteConversacional.COMPANION));
    }

    @Test
    @DisplayName("apagadas: si el modelo igual pide una que propone, no se ejecuta ni se crea nada")
    void siLaPideNoSeEjecuta() {
        HerramientasAgenteService servicio = servicio("ia.propuestas");

        ResultadoHerramienta deLaAdicional = servicio.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos("proponer_algo_de_prueba"));
        ResultadoHerramienta deMarcar = servicio.ejecutar(APRENDIZ, new InvocacionHerramienta(
                CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                Map.of(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID, UUID.randomUUID().toString())));
        ResultadoHerramienta deConsulta = servicio.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos("consultar_algo_de_prueba"));

        assertThat(deLaAdicional).isEqualTo(ResultadoHerramienta.fallo(HerramientasAgenteService.PROPUESTAS_EN_PAUSA));
        assertThat(deMarcar).isEqualTo(ResultadoHerramienta.fallo(HerramientasAgenteService.PROPUESTAS_EN_PAUSA));
        assertThat(propone.llamadas.get()).isZero();
        verify(proponerAccion, never()).proponer(any(), any(), any());
        assertThat(deConsulta).isInstanceOf(ResultadoHerramienta.Exito.class);
    }

    @Test
    @DisplayName("con «ia» apagada (la madre) las propuestas también están apagadas")
    void laMadreApaga() {
        assertThat(nombres(servicio("ia").disponiblesPara(AgenteConversacional.COMPANION, APRENDIZ)))
                .doesNotContain("proponer_algo_de_prueba");
    }

    private static final class HerramientaQueCuenta implements HerramientaAgente {
        private final String nombre;
        final AtomicInteger llamadas = new AtomicInteger();

        HerramientaQueCuenta(String nombre) {
            this.nombre = nombre;
        }

        @Override
        public DefinicionHerramienta definicion() {
            return DefinicionHerramienta.sinParametros(nombre, "De prueba.");
        }

        @Override
        public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
            llamadas.incrementAndGet();
            return ResultadoHerramienta.exito("ok");
        }
    }
}
