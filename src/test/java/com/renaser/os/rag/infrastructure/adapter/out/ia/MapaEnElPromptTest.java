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
    @DisplayName("sin Mapa (retirado, spec 026) el prompt no dice nada de el; si no se pudo leer, tampoco")
    void sinMapaONull() {
        assertThat(MapaEnElPrompt.linea(ResumenDelMapa.de(MapaDeLaPersona.sinMapa(), 23), 23)).isEmpty();
        assertThat(MapaEnElPrompt.linea(ResumenDelMapa.de(MapaDeLaPersona.sinMapa(), 4), 4)).isEmpty();
        assertThat(GoogleGenAiRenasiaChatAdapter.formatearSituacion(DIA_23))
                .isEqualTo(GoogleGenAiRenasiaChatAdapter.formatearSituacion(DIA_23.conMapa(null)))
                .doesNotContain("Mapa de Renacimiento");
    }

    /**
     * D-247 (E-496) y spec 026: sin Rocas Maestras la linea dice que no se proponga nada de objetivos y que los escriba en Plan, tenga o no el Mapa
     * respondido y tenga o no diagnostico. Con ellas, o sin saberlo, como antes.
     */
    @Test
    @DisplayName("D-247: sin Rocas Maestras la linea prohibe proponer objetivos y manda a Plan, nunca al Mapa")
    void sinRocasMaestras() {
        String sinMapa = GoogleGenAiRenasiaChatAdapter.formatearSituacion(DIA_23.conMapa(
                ResumenDelMapa.de(MapaDeLaPersona.sinMapa(), 23).conRocasMaestras(false)));
        assertThat(sinMapa).contains(MapaEnElPrompt.SIN_OBJETIVOS).doesNotContain("Ir al Mapa");
        assertThat(MapaEnElPrompt.SIN_OBJETIVOS).contains("no se puede planificar NINGUN objetivo").contains("abrir la pestana Plan").contains("Tu no puedes escribirlos ni confirmarlos");

        assertThat(MapaEnElPrompt.linea(ResumenDelMapa.de(mapa("salud", List.of()), 23).conRocasMaestras(false), 23))
                .isEqualTo(MapaEnElPrompt.SIN_OBJETIVOS);

        assertThat(MapaEnElPrompt.linea(ResumenDelMapa.de(mapa("salud", List.of()), 23).conRocasMaestras(true), 23))
                .startsWith("Mapa de Renacimiento: prioridad Salud");
        assertThat(MapaEnElPrompt.linea(ResumenDelMapa.de(MapaDeLaPersona.sinMapa(), 23).conRocasMaestras(null), 23)).isEmpty();
    }

    /** Spec 026, 026-8 B: SER sabe del diagnostico en cada turno y lo lee entero con la herramienta. */
    @Test
    @DisplayName("el diagnostico: lo asignado sin terminar, lo completado con su prioridad, y el bloqueo de objetivos sigue diciendolo")
    void elDiagnosticoEnElPrompt() {
        ResumenDelMapa base = ResumenDelMapa.de(MapaDeLaPersona.sinMapa(), 23);

        assertThat(MapaEnElPrompt.linea(base.conDiagnostico(new com.renaser.os.rag.domain.model.mapa.ResumenDelDiagnostico(false, null)), 23))
                .isEqualTo("Diagnostico inicial: lo tiene asignado y todavia no lo termina.\n");
        assertThat(MapaEnElPrompt.linea(base.conDiagnostico(new com.renaser.os.rag.domain.model.mapa.ResumenDelDiagnostico(true, "CUERPO")), 23))
                .isEqualTo("Diagnostico inicial: completado; prioridad principal: Salud. Sus respuestas completas no estan aca: llama consultar_mi_diagnostico.\n");
        assertThat(MapaEnElPrompt.linea(base.conDiagnostico(new com.renaser.os.rag.domain.model.mapa.ResumenDelDiagnostico(true, null)), 23))
                .doesNotContain("prioridad");
        assertThat(MapaEnElPrompt.linea(base.conRocasMaestras(false).conDiagnostico(new com.renaser.os.rag.domain.model.mapa.ResumenDelDiagnostico(true, "TRABAJO")), 23))
                .startsWith(MapaEnElPrompt.SIN_OBJETIVOS).contains("Negocio y dinero");
        assertThat(MapaEnElPrompt.linea(base.conDiagnostico(null), 23)).isEmpty();
    }
}
