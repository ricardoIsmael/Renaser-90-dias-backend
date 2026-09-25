package com.renaser.os.rag.infrastructure.adapter.in.rest.voz;

import com.renaser.os.rag.application.ports.in.voz.VozDelOrbeUseCase;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Cuerpo de {@code POST /api/v1/renasia/voz}: el texto que el orbe va a decir en voz alta. Mismo
 * tope que el caso de uso ({@link VozDelOrbeUseCase#LARGO_MAXIMO_TEXTO}); aca se corta antes
 * de llegar a el, con el 400 de validacion de siempre.
 */
public record SintetizarVozRequest(@NotBlank @Size(max = VozDelOrbeUseCase.LARGO_MAXIMO_TEXTO) String texto) {
}
