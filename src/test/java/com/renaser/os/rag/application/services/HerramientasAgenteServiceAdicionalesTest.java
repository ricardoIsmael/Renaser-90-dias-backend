package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.application.services.herramientas.HerramientaAgente;
import com.renaser.os.rag.application.services.herramientas.PropuestaDeMarcarHabito;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.herramienta.CatalogoHerramientasAgente;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * El punto de extension de {@link HerramientaAgente} (2026-09-23): las herramientas nuevas del
 * acompanante viven cada una en su clase y el servicio las ofrece y las ejecuta con las mismas
 * garantias que las tres originales.
 */
class HerramientasAgenteServiceAdicionalesTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private final ConsultarAgendaHabitosPort agenda = mock(ConsultarAgendaHabitosPort.class);

    /** Flag de la fase 2 apagado: estos casos son sobre las adicionales, no sobre las propuestas. */
    private PropuestaDeMarcarHabito sinPropuesta() {
        return new PropuestaDeMarcarHabito(agenda, mock(ProponerAccionUseCase.class), false);
    }

    @Test
    @DisplayName("el acompanante recibe las originales y las adicionales; Sparkie ninguna")
    void seOfrecenSoloAlAcompanante() {
        var servicio = new HerramientasAgenteService(agenda, List.of(new HerramientaDeEco()), sinPropuesta(),
                com.renaser.os.shared.domain.FixedClock.at(java.time.Instant.parse("2026-09-25T15:00:00Z")));

        assertThat(servicio.disponibles(AgenteConversacional.COMPANION))
                .extracting(DefinicionHerramienta::nombre)
                .containsExactly(CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA,
                        CatalogoHerramientasAgente.CONSULTAR_PUNTOS_EN_JUEGO,
                        CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO, HerramientaDeEco.NOMBRE);
        assertThat(servicio.disponibles(AgenteConversacional.COURSE_TUTOR)).isEmpty();
    }

    @Test
    @DisplayName("una adicional se ejecuta con el actor de la conversacion y sus argumentos")
    void seEjecutaConElActor() {
        var servicio = new HerramientasAgenteService(agenda, List.of(new HerramientaDeEco()), sinPropuesta(),
                com.renaser.os.shared.domain.FixedClock.at(java.time.Instant.parse("2026-09-25T15:00:00Z")));

        var resultado = servicio.ejecutar(APRENDIZ,
                new InvocacionHerramienta(HerramientaDeEco.NOMBRE, Map.of("texto", "hola")));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.exito(APRENDIZ + ":hola"));
    }

    @Test
    @DisplayName("a una adicional tambien se le exigen sus argumentos obligatorios antes de ejecutarla")
    void seValidanLosObligatorios() {
        var servicio = new HerramientasAgenteService(agenda, List.of(new HerramientaDeEco()), sinPropuesta(),
                com.renaser.os.shared.domain.FixedClock.at(java.time.Instant.parse("2026-09-25T15:00:00Z")));

        var resultado = servicio.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(HerramientaDeEco.NOMBRE));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("texto");
    }

    @Test
    @DisplayName("si una adicional lanza, el modelo recibe un motivo legible y no la excepcion")
    void unaExcepcionSeTraduce() {
        var servicio = new HerramientasAgenteService(agenda, List.of(new HerramientaQueRevienta()), sinPropuesta(),
                com.renaser.os.shared.domain.FixedClock.at(java.time.Instant.parse("2026-09-25T15:00:00Z")));

        var resultado = servicio.ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(HerramientaQueRevienta.NOMBRE));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).doesNotContain("IllegalStateException");
    }

    @Test
    @DisplayName("dos herramientas con el mismo nombre cortan el arranque en vez de ser ambiguas")
    void nombreRepetidoCortaElArranque() {
        HerramientaAgente copiaDeUnaOriginal = new HerramientaAgente() {
            @Override
            public DefinicionHerramienta definicion() {
                return DefinicionHerramienta.sinParametros(CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA, "x");
            }

            @Override
            public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
                return ResultadoHerramienta.exito("x");
            }
        };

        assertThatThrownBy(() -> new HerramientasAgenteService(agenda, List.of(copiaDeUnaOriginal), sinPropuesta(),
                com.renaser.os.shared.domain.FixedClock.at(java.time.Instant.parse("2026-09-25T15:00:00Z"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA);
    }

    private static final class HerramientaDeEco implements HerramientaAgente {
        static final String NOMBRE = "eco_de_prueba";

        @Override
        public DefinicionHerramienta definicion() {
            return new DefinicionHerramienta(NOMBRE, "Devuelve el texto recibido.", List.of(
                    ParametroHerramienta.obligatorio("texto", TipoParametroHerramienta.TEXTO, "lo que se repite")));
        }

        @Override
        public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
            return ResultadoHerramienta.exito(actorId + ":" + invocacion.argumento("texto"));
        }
    }

    private static final class HerramientaQueRevienta implements HerramientaAgente {
        static final String NOMBRE = "revienta_de_prueba";

        @Override
        public DefinicionHerramienta definicion() {
            return DefinicionHerramienta.sinParametros(NOMBRE, "Siempre falla.");
        }

        @Override
        public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
            throw new IllegalStateException("se rompio algo interno");
        }
    }
}
