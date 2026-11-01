package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.api.Analisis8020Finder;
import com.renaser.os.rag.api.Analisis8020Finder.AnalisisLeido;
import com.renaser.os.rag.api.Analisis8020Finder.TextoVisible;
import com.renaser.os.rag.api.Analisis8020Finder.Tipo;
import com.renaser.os.rag.application.services.circuito.InterruptoresDeSer;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

/** {@code consultar_analisis_80_20} (spec 031, FR-026): solo el propio, solo lo guardado, y sin llevar lo emocional a la memoria. */
class ConsultarAnalisis8020HerramientaTest {

    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final InvocacionHerramienta INVOCACION = InvocacionHerramienta.sinArgumentos(ConsultarAnalisis8020Herramienta.NOMBRE);

    private final Analisis8020Finder analisis = mock(Analisis8020Finder.class);
    private final InterruptoresDeSer interruptores = mock(InterruptoresDeSer.class);
    private final ConsultarAnalisis8020Herramienta herramienta = new ConsultarAnalisis8020Herramienta(analisis, interruptores);

    private static AnalisisLeido leido(Tipo tipo, String movio) {
        return new AnalisisLeido(tipo, 10, LocalDate.of(2026, 10, 19), LocalDate.of(2026, 10, 25),
                new TextoVisible(movio, "La llamada quedó pendiente.", "Bajas por las tardes.", List.of("Haz la llamada primero.", "Entrena temprano.")),
                Instant.parse("2026-10-26T06:00:00Z"));
    }

    @Test
    @DisplayName("muestra el análisis de la última semana y del último mes ya guardados, solo del que pregunta")
    void propioYGuardado() {
        when(interruptores.analisisPrendido()).thenReturn(true);
        when(analisis.ultimoListo(PERSONA, Tipo.SEMANA)).thenReturn(Optional.of(leido(Tipo.SEMANA, "Entrenar movió más.")));
        when(analisis.ultimoListo(PERSONA, Tipo.MES)).thenReturn(Optional.of(leido(Tipo.MES, "Sostuviste el gimnasio.")));

        String texto = ((ResultadoHerramienta.Exito) herramienta.ejecutar(PERSONA, INVOCACION)).contenido();

        assertThat(texto).contains("su ultima semana").contains("su ultimo mes").contains("Entrenar movió más.")
                .contains("Sostuviste el gimnasio.").contains("- Haz la llamada primero.").contains("- Entrena temprano.");
        verify(analisis).ultimoListo(PERSONA, Tipo.SEMANA);
        verify(analisis).ultimoListo(PERSONA, Tipo.MES);
        verifyNoMoreInteractions(analisis);
    }

    @Test
    @DisplayName("FR-026: le pide al modelo no cambiar cifras, no etiquetar y no llevar lo emocional ni lo de salud a su memoria; lo que cambie el plan, con botón")
    void comoUsarlo() {
        when(interruptores.analisisPrendido()).thenReturn(true);
        when(analisis.ultimoListo(PERSONA, Tipo.SEMANA)).thenReturn(Optional.of(leido(Tipo.SEMANA, "x")));
        when(analisis.ultimoListo(PERSONA, Tipo.MES)).thenReturn(Optional.empty());

        String texto = ((ResultadoHerramienta.Exito) herramienta.ejecutar(PERSONA, INVOCACION)).contenido();

        assertThat(texto).contains("sin cambiar ninguna cifra").contains("No diagnostiques").contains("propuesta con boton de confirmar")
                .contains("No guardes en tu memoria lo emocional ni lo de su salud");
    }

    @Test
    @DisplayName("sin ningún análisis lo dice y explica cuándo se genera; no inventa")
    void sinAnalisis() {
        when(interruptores.analisisPrendido()).thenReturn(true);
        when(analisis.ultimoListo(PERSONA, Tipo.SEMANA)).thenReturn(Optional.empty());
        when(analisis.ultimoListo(PERSONA, Tipo.MES)).thenReturn(Optional.empty());

        String texto = ((ResultadoHerramienta.Exito) herramienta.ejecutar(PERSONA, INVOCACION)).contenido();

        assertThat(texto).contains("Todavia no tiene ningun analisis 80/20").contains("al cerrar cada semana y cada mes");
    }

    @Test
    @DisplayName("FR-019: con ia.analisis apagada no se ofrece el texto: mantenimiento, y no se lee nada")
    void apagada() {
        when(interruptores.analisisPrendido()).thenReturn(false);

        assertThat(herramienta.ejecutar(PERSONA, INVOCACION)).isInstanceOf(ResultadoHerramienta.Fallo.class);
        org.mockito.Mockito.verifyNoInteractions(analisis);
    }

    @Test
    @DisplayName("una falla al leer es un Fallo legible, nunca una excepción")
    void fallaAlLeer() {
        when(interruptores.analisisPrendido()).thenReturn(true);
        when(analisis.ultimoListo(PERSONA, Tipo.SEMANA)).thenThrow(new IllegalStateException("base caída"));

        assertThat(herramienta.ejecutar(PERSONA, INVOCACION)).isInstanceOf(ResultadoHerramienta.Fallo.class);
    }

    @Test
    @DisplayName("no tiene argumentos: no hay forma de pedir el análisis de otra persona")
    void sinArgumentos() {
        assertThat(herramienta.definicion().nombre()).isEqualTo("consultar_analisis_80_20");
        assertThat(herramienta.definicion().parametros()).isEmpty();
    }
}
