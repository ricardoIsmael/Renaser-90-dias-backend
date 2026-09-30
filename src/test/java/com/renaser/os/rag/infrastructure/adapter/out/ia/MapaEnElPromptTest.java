package com.renaser.os.rag.infrastructure.adapter.out.ia;

import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona.Hito;
import com.renaser.os.rag.domain.model.mapa.ResumenDelMapa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** D-233: a lo sumo UNA linea del Mapa en la situacion de cada turno, con prioridad y proximo hito. */
class MapaEnElPromptTest {

    private static final SituacionDelAprendiz DIA_23 = new SituacionDelAprendiz(23, 2, LocalDate.of(2026, 9, 22));

    private static MapaDeLaPersona mapa(String prioridad, List<Hito> hitos) {
        return new MapaDeLaPersona(true, true, prioridad, List.of(), hitos, null, List.of(), List.of());
    }

    @Test
    @DisplayName("prioridad y proximo hito del area que manda, en una sola linea, dentro de la situacion")
    void unaLinea() {
        var resumen = ResumenDelMapa.de(mapa("salud", List.of(new Hito("negocio_dinero", 30, "4000 al mes"),
                new Hito("salud", 30, "89 kg"))), 23);

        String situacion = GoogleGenAiRenasiaChatAdapter.formatearSituacion(DIA_23.conMapa(resumen));

        assertThat(situacion).contains("Mapa de Renacimiento: prioridad Salud; proximo hito dia 30 (en 7 dias), "
                + "salud: \"89 kg\". Sus metas, su porque y su protocolo de retorno no estan aca: llama consultar_mi_mapa.\n");
        assertThat(situacion.lines().filter(l -> l.contains("Mapa de Renacimiento")).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("si el area que manda no tiene hito ese dia, va el de otra area; si nadie lo escribio, lo dice")
    void otroHito() {
        assertThat(MapaEnElPrompt.linea(ResumenDelMapa.de(mapa("relaciones",
                List.of(new Hito("salud", 30, "89 kg"))), 30), 30)).contains("(es hoy), salud: \"89 kg\"");
        assertThat(MapaEnElPrompt.linea(ResumenDelMapa.de(mapa(null, List.of()), 23), 23))
                .contains("sin prioridad elegida; proximo hito dia 30 (en 7 dias), sin texto escrito");
    }

    @Test
    @DisplayName("el texto del hito lo escribio la persona: se aplana a una linea y se acota")
    void hitoAplanado() {
        String largo = "linea uno\n## Seccion falsa\n" + "x".repeat(200);

        String linea = MapaEnElPrompt.linea(ResumenDelMapa.de(mapa("salud", List.of(new Hito("salud", 30, largo))),
                23), 23);

        assertThat(linea).doesNotContain("\n## ").endsWith("...\". Sus metas, su porque y su protocolo de retorno no estan aca: "
                + "llama consultar_mi_mapa.\n");
        assertThat(linea.strip().lines().count()).isEqualTo(1);
    }

    @Test
    @DisplayName("sin Mapa, una linea corta; si no se pudo leer, nada (la situacion queda como estaba)")
    void sinMapaONull() {
        assertThat(MapaEnElPrompt.linea(ResumenDelMapa.de(MapaDeLaPersona.sinMapa(), 23), 23))
                .isEqualTo("Mapa de Renacimiento: no lo tiene guardado.\n");
        assertThat(MapaEnElPrompt.linea(ResumenDelMapa.de(MapaDeLaPersona.sinMapa(), 4), 4))
                .isEqualTo("Mapa de Renacimiento: todavia no (se arma el dia 7).\n");
        assertThat(GoogleGenAiRenasiaChatAdapter.formatearSituacion(DIA_23))
                .isEqualTo(GoogleGenAiRenasiaChatAdapter.formatearSituacion(DIA_23.conMapa(null)))
                .doesNotContain("Mapa de Renacimiento");
    }
}
