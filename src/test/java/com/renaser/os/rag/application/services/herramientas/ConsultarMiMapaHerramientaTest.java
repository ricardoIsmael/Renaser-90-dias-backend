package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.mapa.ConsultarMapaDeRenacimientoPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona.Accion;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona.Hito;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona.Objetivo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** D-233: {@code consultar_mi_mapa} devuelve el Mapa con las palabras de la persona y marca el proximo hito. */
class ConsultarMiMapaHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    /** Martes 22/09/2026 en su zona, su dia 23: el hito del dia 30 cae el martes 29/09. */
    private static final SituacionDelAprendiz DIA_23 = new SituacionDelAprendiz(23, 2, LocalDate.of(2026, 9, 22));

    static final MapaDeLaPersona MAPA = new MapaDeLaPersona(true, true, "salud",
            List.of(new Objetivo("salud", "peso", "92", "85", "kg", null, null, "foto de la balanza",
                            "quiero jugar con mis hijos sin cansarme", "Bajar de 92 a 85 kg al dia 90"),
                    new Objetivo("negocio_dinero", "ventas", "3000", "6000", "PEN", "mensual", null,
                            "reporte del banco", "quiero dejar de endeudarme", null),
                    new Objetivo("relaciones", "pareja", "5", "8", "de 10", null, "escuchar sin el celular",
                            null, "la quiero de vuelta cerca", null)),
            List.of(new Hito("salud", 30, "89 kg"), new Hito("negocio_dinero", 30, "4000 al mes"),
                    new Hito("salud", 60, "87 kg"), new Hito("salud", 90, "85 kg")),
            "caminar 10 minutos y tomar agua",
            List.of(new Accion("salud", "Caminar 40 minutos", 5)),
            List.of("Cuando llego cansado, en lugar de abrir el refri, hare tomar agua."));

    private static String ejecutar(MapaDeLaPersona mapa, Optional<SituacionDelAprendiz> situacion) {
        ConsultarMapaDeRenacimientoPort mapaPort = participanteId -> mapa;
        ConsultarSituacionDelAprendizPort situacionPort = participanteId -> situacion;
        ResultadoHerramienta resultado = new ConsultarMiMapaHerramienta(mapaPort, situacionPort)
                .ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(ConsultarMiMapaHerramienta.NOMBRE));
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }

    @Test
    @DisplayName("se ofrece como consultar_mi_mapa, sin parametros, y la descripcion dice cuando usarla")
    void definicion() {
        var definicion = new ConsultarMiMapaHerramienta(id -> MAPA, id -> Optional.empty()).definicion();

        assertThat(definicion.nombre()).isEqualTo("consultar_mi_mapa");
        assertThat(definicion.parametros()).isEmpty();
        assertThat(definicion.descripcion()).contains("Mapa de Renacimiento").contains("protocolo de retorno")
                .contains("para que hace un habito").contains("se desanima");
    }

    @Test
    @DisplayName("prioridad, cada objetivo con linea base -> dia 90, evidencia y porque, acciones y retorno")
    void mapaCompleto() {
        String texto = ejecutar(MAPA, Optional.of(DIA_23));

        assertThat(texto).contains("Prioridad: Salud")
                .contains("- Salud (peso): Meta: \"Bajar de 92 a 85 kg al dia 90\". Hoy 92 kg -> dia 90: 85 kg. "
                        + "Evidencia: foto de la balanza. Por que: quiero jugar con mis hijos sin cansarme.")
                .contains("- Negocio y dinero (ventas): Hoy 3000 PEN -> dia 90: 6000 PEN (mensual).")
                .contains("- Relaciones (pareja): Hoy 5 de 10 -> dia 90: 8 de 10. Va a hacer distinto: "
                        + "escuchar sin el celular.")
                .contains("Acciones que eligio para llegar: Caminar 40 minutos (salud, 5 veces por semana).")
                .contains("Protocolo de retorno (su accion minima para volver en menos de 24 h si se cae): "
                        + "\"caminar 10 minutos y tomar agua\"")
                .contains("Sus reemplazos: Cuando llego cansado")
                .contains("No agregues metas, numeros ni hitos que no esten aca.");
    }

    @Test
    @DisplayName("marca el proximo hito con los dias que faltan y su fecha en su zona; los pasados y los que siguen, sin marca")
    void proximoHito() {
        String texto = ejecutar(MAPA, Optional.of(DIA_23));

        assertThat(texto).contains("Hitos, pasos hacia la meta del dia 90 (hoy es su dia 23 de 90):")
                .contains("- Dia 30 [PROXIMO: faltan 7 dias, el martes 29/09/2026]: Salud: 89 kg | "
                        + "Negocio y dinero: 4000 al mes")
                .contains("- Dia 60: Salud: 87 kg")
                .contains("- Dia 90: Salud: 85 kg");

        String enElDia45 = ejecutar(MAPA, Optional.of(new SituacionDelAprendiz(45, 3, LocalDate.of(2026, 10, 14))));
        assertThat(enElDia45).contains("- Dia 30 (ya paso): Salud: 89 kg")
                .contains("- Dia 60 [PROXIMO: faltan 15 dias, el jueves 29/10/2026]: Salud: 87 kg");

        String elMismoDia = ejecutar(MAPA, Optional.of(new SituacionDelAprendiz(60, 3, LocalDate.of(2026, 10, 29))));
        assertThat(elMismoDia).contains("- Dia 60 [PROXIMO: es hoy]");
    }

    @Test
    @DisplayName("sin dia de programa sale el Mapa igual, pero sin decir cual es el proximo hito")
    void sinDia() {
        String texto = ejecutar(MAPA, Optional.empty());

        assertThat(texto).contains("no se su dia del programa").doesNotContain("PROXIMO").contains("- Dia 30: Salud");
    }

    @Test
    @DisplayName("sin Mapa: lo dice y pide no inventar; antes del dia 7 aclara que se arma ese dia")
    void sinMapa() {
        assertThat(ejecutar(MapaDeLaPersona.sinMapa(), Optional.of(DIA_23)))
                .contains("No tiene Mapa de Renacimiento guardado").contains("No le inventes metas ni hitos")
                .contains("invitala a completar su Mapa");
        assertThat(ejecutar(MapaDeLaPersona.sinMapa(), Optional.of(new SituacionDelAprendiz(4, 1))))
                .contains("se arma el dia 7 del programa y hoy es su dia 4");
    }

    @Test
    @DisplayName("a medias: lo avisa y no rellena lo que falta")
    void aMedias() {
        MapaDeLaPersona aMedias = new MapaDeLaPersona(true, false, null, List.of(), List.of(), null, List.of(),
                List.of());

        assertThat(ejecutar(aMedias, Optional.of(DIA_23))).contains("lo dejo a medias")
                .contains("Prioridad: no la eligio").contains("Objetivos: no escribio ninguno")
                .contains("- Dia 30 [PROXIMO: faltan 7 dias, el martes 29/09/2026]: no escribio hitos para este dia.")
                .contains("Protocolo de retorno: no lo escribio.");
    }

    @Test
    @DisplayName("si el Mapa no se puede leer es un fallo explicable, no una excepcion")
    void falla() {
        ConsultarMapaDeRenacimientoPort roto = participanteId -> {
            throw new IllegalStateException("caida");
        };

        ResultadoHerramienta resultado = new ConsultarMiMapaHerramienta(roto, id -> Optional.of(DIA_23))
                .ejecutar(APRENDIZ, InvocacionHerramienta.sinArgumentos(ConsultarMiMapaHerramienta.NOMBRE));

        assertThat(resultado).isEqualTo(ResultadoHerramienta.fallo("No pude leer su Mapa de Renacimiento en este momento."));
    }
}
