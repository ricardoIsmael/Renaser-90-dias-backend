package com.renaser.os.chat.infrastructure.adapter.in.rest.ranking;

import com.renaser.os.chat.application.ports.in.ranking.VerPodioDeLaSemanaUseCase.VistaPreviaDelPodio;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

/**
 * La vista previa del podio (D-262). La imagen viaja como {@code data:} URL, así se ve tal cual pegándola en el
 * navegador; {@code null} con el texto si nadie tiene puntaje (no se publicaría).
 */
public record VistaPreviaDelPodioResponse(LocalDate weekStart, LocalDate weekEnd, String template,
                                          boolean alreadyPublished, List<Entry> entries, String text, String image) {

    public record Entry(int place, String name, BigDecimal score) {
    }

    static VistaPreviaDelPodioResponse from(VistaPreviaDelPodio vista) {
        return new VistaPreviaDelPodioResponse(vista.lunes(), vista.domingo(), vista.plantilla(), vista.yaPublicado(),
                vista.puestos().stream().map(p -> new Entry(p.lugar(), p.nombre(), p.puntaje())).toList(),
                vista.texto(), comoDataUrl(vista.imagen(), vista.tipoDeImagen()));
    }

    private static String comoDataUrl(byte[] imagen, String tipo) {
        return imagen == null ? null : "data:" + tipo + ";base64," + Base64.getEncoder().encodeToString(imagen);
    }
}
