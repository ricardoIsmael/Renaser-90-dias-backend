package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.mapa.ConsultarMapaDeRenacimientoPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort;
import com.renaser.os.rag.application.ports.out.participante.ConsultarSituacionDelAprendizPort.SituacionDelAprendiz;
import com.renaser.os.rag.domain.model.mapa.MapaDeLaPersona;
import com.renaser.os.rag.domain.model.mapa.ProximoHito;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * El objetivo del Mapa de Renacimiento al que apunta una propuesta de rocas (D-233, pedido del dueno del
 * 30-09): antes de proponer un objetivo semanal o acciones del dia, la propuesta dice a que objetivo de SU
 * Mapa empuja ("Para tu objetivo de salud: Bajar de 92 a 85 kg al dia 90."), segun el eje (CUERPO es
 * salud, TRABAJO es negocio y dinero, RELACIONES es relaciones).
 *
 * <p>Es para los OBJETIVOS (rocas semanales y acciones del dia), que cuelgan de la Roca Maestra de su eje, y
 * esta de su objetivo del Mapa; los habitos no pasan por aca (pedido del dueno: no se fuerzan a un objetivo).
 * Con el dia del programa, suma el proximo hito de esa area como guia ("Proximo hito, dia 60: 87 kg.").
 *
 * <p>Nunca bloquea la propuesta: si el Mapa no se puede leer, sale como antes. Lo que no puede decidir el
 * codigo —si "correr 5 km" apunta de verdad a la meta de salud— se le dice al modelo en el resultado para
 * que, si no se relaciona, lo diga en una linea y sugiera como conectarlo.
 */
@Component
public class MapaParaProponer {

    private static final Logger log = LoggerFactory.getLogger(MapaParaProponer.class);

    private final ConsultarMapaDeRenacimientoPort mapaPort;
    private final ConsultarSituacionDelAprendizPort situacionPort;

    public MapaParaProponer(ConsultarMapaDeRenacimientoPort mapaPort, ConsultarSituacionDelAprendizPort situacionPort) {
        this.mapaPort = mapaPort;
        this.situacionPort = situacionPort;
    }

    /** Lo que hay que agregar a la propuesta para esos ejes: al resumen de la tarjeta y al resultado del modelo. */
    public Vinculo para(UserId actorId, Collection<String> ejes) {
        List<String> ejesSinRepetir = List.copyOf(new LinkedHashSet<>(ejes));
        try {
            MapaDeLaPersona mapa = mapaPort.de(actorId);
            Integer dia = mapa.tieneMapa() ? situacionPort.de(actorId).map(SituacionDelAprendiz::diaPrograma).orElse(null)
                    : null;
            return vinculo(mapa, ejesSinRepetir, dia);
        } catch (RuntimeException falla) {
            log.info("[rag] la propuesta sale sin el Mapa: {}", falla.toString());
            return new Vinculo("", "");
        }
    }

    /** @param diaDeHoy dia del programa, o {@code null} si no se sabe (entonces sin proximo hito) */
    static Vinculo vinculo(MapaDeLaPersona mapa, List<String> ejes, Integer diaDeHoy) {
        if (!mapa.tieneMapa()) {
            return new Vinculo("", "\nNo tiene Mapa de Renacimiento: la propuesta va igual; invitala en una linea a "
                    + "completar su Mapa para conectar sus rocas con sus objetivos.");
        }
        List<String> conObjetivo = ejes.stream()
                .map(eje -> objetivoDelEje(mapa, eje, diaDeHoy)).flatMap(Optional::stream).toList();
        String resumen = conObjetivo.isEmpty() ? "" : " " + String.join(" ", conObjetivo);
        return new Vinculo(resumen, paraElModelo(mapa, ejes, conObjetivo.isEmpty()));
    }

    /** "Para tu objetivo de salud: Bajar de 92 a 85 kg al dia 90. Proximo hito, dia 60: 87 kg." */
    private static Optional<String> objetivoDelEje(MapaDeLaPersona mapa, String eje, Integer diaDeHoy) {
        String area = MapaDeLaPersona.areaDelEje(eje);
        return Optional.ofNullable(area).flatMap(mapa::objetivoDelArea)
                .map(objetivo -> "Para tu objetivo de " + MapaDeLaPersona.nombreDeArea(area).toLowerCase(Locale.ROOT)
                        + ": " + objetivo.enUnaFrase() + "." + proximoHito(mapa, area, diaDeHoy));
    }

    private static String proximoHito(MapaDeLaPersona mapa, String area, Integer diaDeHoy) {
        return Optional.ofNullable(diaDeHoy).flatMap(ProximoHito::para)
                .flatMap(proximo -> mapa.hitosDelDia(proximo.dia()).stream()
                        .filter(hito -> hito.area().equals(area)).findFirst())
                .map(hito -> " Proximo hito, dia " + hito.dia() + ": " + hito.texto() + ".")
                .orElse("");
    }

    private static String paraElModelo(MapaDeLaPersona mapa, List<String> ejes, boolean ningunoConObjetivo) {
        String sinObjetivo = ejes.stream()
                .map(MapaDeLaPersona::areaDelEje).filter(Objects::nonNull)
                .filter(area -> mapa.objetivoDelArea(area).isEmpty())
                .map(MapaDeLaPersona::nombreDeArea).collect(Collectors.joining(", "));
        String aviso = sinObjetivo.isEmpty() ? "" : "\nSu Mapa no tiene objetivo de " + sinObjetivo
                + ": diselo en una linea y sugiere como conectarlo con uno de sus objetivos.";
        return ningunoConObjetivo ? aviso : aviso + "\nSi lo propuesto no apunta a ese objetivo de su Mapa, diselo en "
                + "una linea y sugiere como conectarlo; si insiste, la propuesta queda igual.";
    }

    /**
     * @param enElResumen lo que se agrega al resumen de la tarjeta ("" si no hay objetivo que nombrar)
     * @param paraElModelo lo que se agrega al resultado que lee el modelo, con la instruccion de que hacer
     */
    public record Vinculo(String enElResumen, String paraElModelo) {
    }
}
