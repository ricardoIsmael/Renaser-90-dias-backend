package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.espejosombra.ListarInformesEspejoSombraUseCase;
import com.renaser.os.rag.domain.model.espejosombra.DistribucionTemporal;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombra;
import com.renaser.os.rag.domain.model.espejosombra.InformeEspejoSombraId;
import com.renaser.os.rag.domain.model.espejosombra.PreguntaConfrontacion;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@code consultar_espejo_de_la_sombra}: solo el propio, el mas reciente, en tono neutro. */
class ConsultarEspejoDeLaSombraHerramientaTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final InvocacionHerramienta INVOCACION =
            InvocacionHerramienta.sinArgumentos(ConsultarEspejoDeLaSombraHerramienta.NOMBRE);

    private final ListarInformesEspejoSombraUseCase listar = mock(ListarInformesEspejoSombraUseCase.class);
    private final ConsultarEspejoDeLaSombraHerramienta herramienta = new ConsultarEspejoDeLaSombraHerramienta(listar);

    private static InformeEspejoSombra informe(LocalDate semana, String patron) {
        return InformeEspejoSombra.rehydrate(InformeEspejoSombraId.of(UUID.randomUUID()), PERSONA, semana, 5, patron,
                new DistribucionTemporal(50, 30, 20), "Vuelve seguido a lo que no salio",
                List.of(new PreguntaConfrontacion(2, "Que harias distinto?"),
                        new PreguntaConfrontacion(1, "Que te dice eso?")), Instant.parse("2026-09-21T10:00:00Z"));
    }

    @Test
    @DisplayName("pide los informes de quien pregunta, como participante, y muestra el mas reciente")
    void propioYMasReciente() {
        when(listar.deParticipante(PERSONA, PERSONA)).thenReturn(List.of(
                informe(LocalDate.of(2026, 9, 21), "Autoexigencia"), informe(LocalDate.of(2026, 9, 14), "Otro")));

        String texto = ((ResultadoHerramienta.Exito) herramienta.ejecutar(PERSONA, INVOCACION)).contenido();

        verify(listar).deParticipante(PERSONA, PERSONA);
        assertThat(texto).contains("semana que empezo el lunes 21/09").contains("5 entradas")
                .contains("Patron que aparece: Autoexigencia").doesNotContain("Otro")
                .contains("pasado 50%, presente 30%, futuro 20%")
                .contains("1. Que te dice eso?\n2. Que harias distinto?")
                .contains("no un diagnostico");
    }

    @Test
    @DisplayName("sin informes lo dice; cuenta suspendida, Fallo legible")
    void vacioYSuspendida() {
        when(listar.deParticipante(PERSONA, PERSONA)).thenReturn(List.of());
        assertThat(((ResultadoHerramienta.Exito) herramienta.ejecutar(PERSONA, INVOCACION)).contenido())
                .startsWith("Todavia no tiene ningun informe");

        when(listar.deParticipante(PERSONA, PERSONA)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));
        assertThat(herramienta.ejecutar(PERSONA, INVOCACION)).isInstanceOf(ResultadoHerramienta.Fallo.class);
    }
}
