package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.conversacion.LoadMensajeRenasiaPort;
import com.renaser.os.rag.domain.model.conversacion.AgenteConversacional;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasia;
import com.renaser.os.rag.domain.model.conversacion.MensajeRenasiaId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** E-456: "solo el sabado" es un dia; "los sabados", "todos los sabados", "cada sabado", todas las semanas. */
class UnDiaOCadaSemanaTest {

    /** 01:00 UTC: las 20:00 del dia anterior en Lima (regla 03). La ventana es relativa, no depende de la zona. */
    private static final Instant AHORA = Instant.parse("2026-10-01T01:00:00Z");
    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    @ParameterizedTest
    @ValueSource(strings = {"mejor no, entonces apagala solo el sabado", "el sábado no la hago",
            "apaga escritura este SABADO", "entonces apagala solo el sabado"})
    @DisplayName("un solo sabado")
    void unSoloDia(String pregunta) {
        assertThat(UnDiaOCadaSemana.esUnSoloDia(pregunta, DayOfWeek.SATURDAY)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"apagala los sabados", "todos los sábados no", "cada sabado la salto",
            "el sabado, todas las semanas", "si", "apagala manana"})
    @DisplayName("todas las semanas, o un mensaje que no nombra el dia: no decide")
    void noEsUnSoloDia(String pregunta) {
        assertThat(UnDiaOCadaSemana.esUnSoloDia(pregunta, DayOfWeek.SATURDAY)).isFalse();
    }

    @Test
    @DisplayName("lee el ultimo mensaje de la persona, y solo si es reciente")
    void leeElUltimoMensajeReciente() {
        LoadMensajeRenasiaPort mensajes = mock(LoadMensajeRenasiaPort.class);
        UnDiaOCadaSemana servicio = new UnDiaOCadaSemana(mensajes, FixedClock.at(AHORA));
        when(mensajes.pagina(APRENDIZ, AgenteConversacional.COMPANION, null, 2)).thenReturn(List.of(
                MensajeRenasia.escribirDeUsuario(MensajeRenasiaId.of(UUID.randomUUID()), APRENDIZ,
                        AgenteConversacional.COMPANION, "mejor no, entonces apagala solo el sabado",
                        AHORA.minusSeconds(20))));

        assertThat(servicio.pidioUnSoloDia(APRENDIZ, DayOfWeek.SATURDAY)).isTrue();

        UnDiaOCadaSemana tarde = new UnDiaOCadaSemana(mensajes, FixedClock.at(AHORA.plusSeconds(600)));
        assertThat(tarde.pidioUnSoloDia(APRENDIZ, DayOfWeek.SATURDAY)).isFalse();
    }
}
