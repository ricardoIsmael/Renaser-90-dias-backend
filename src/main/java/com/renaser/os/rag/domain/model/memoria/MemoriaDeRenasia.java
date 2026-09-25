package com.renaser.os.rag.domain.model.memoria;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Todo lo que el acompanante sabe de una persona (D-167): sus recuerdos, por categoria, y el
 * resumen de lo que venian conversando. Es lo que entra al prompt y lo que la persona ve en su
 * perfil.
 *
 * @param resumen         lo conversado antes de los ultimos mensajes; falta si todavia no se compacto
 *                        nada, o si la persona borro algo (el resumen podia nombrarlo)
 * @param compactadoHasta hasta donde ya se leyo la conversacion: la proxima compactacion arranca
 *                        despues. No retrocede nunca, ni al borrar: si no, lo borrado volveria
 */
public record MemoriaDeRenasia(List<Recuerdo> recuerdos, Optional<String> resumen, Instant compactadoHasta) {

    public static final int LARGO_MAXIMO_DEL_RESUMEN = 2000;

    private static final MemoriaDeRenasia VACIA = new MemoriaDeRenasia(List.of(), Optional.empty(), Instant.EPOCH);

    public MemoriaDeRenasia {
        recuerdos = List.copyOf(Objects.requireNonNull(recuerdos, "recuerdos es obligatorio"));
        resumen = Objects.requireNonNull(resumen, "resumen es obligatorio").map(String::strip)
                .filter(texto -> !texto.isEmpty());
        Objects.requireNonNull(compactadoHasta, "compactadoHasta es obligatorio");
        if (resumen.map(String::length).orElse(0) > LARGO_MAXIMO_DEL_RESUMEN) {
            throw new IllegalArgumentException("El resumen tiene hasta " + LARGO_MAXIMO_DEL_RESUMEN + " caracteres");
        }
    }

    public static MemoriaDeRenasia vacia() {
        return VACIA;
    }

    public boolean estaVacia() {
        return recuerdos.isEmpty() && resumen.isEmpty();
    }

    /** En el orden de la categoria, y dentro de cada una en el orden en que se guardaron. */
    public Map<CategoriaDeRecuerdo, List<Recuerdo>> porCategoria() {
        return recuerdos.stream().collect(Collectors.groupingBy(Recuerdo::categoria,
                () -> new EnumMap<>(CategoriaDeRecuerdo.class), Collectors.toList()));
    }

    /** El que ya estaba con ese mismo texto: al compactar conserva su id y su fecha. */
    public Optional<Recuerdo> recuerdo(CategoriaDeRecuerdo categoria, String texto) {
        return recuerdos.stream()
                .filter(recuerdo -> recuerdo.categoria() == categoria && recuerdo.texto().equals(texto))
                .findFirst();
    }

    /**
     * El bloque "Lo que sabes de esta persona" del prompt. Sin memoria lo dice, para que el modelo
     * no invente una: "todavia no sabes nada en particular".
     */
    public String paraElModelo() {
        if (estaVacia()) {
            return "Todavia no sabes nada en particular de esta persona: conocela conversando.";
        }
        StringBuilder texto = new StringBuilder();
        porCategoria().forEach((categoria, deEsta) -> {
            texto.append(categoria.paraElModelo()).append(":\n");
            deEsta.forEach(recuerdo -> texto.append("- ").append(recuerdo.texto()).append('\n'));
        });
        resumen.ifPresent(r -> texto.append("Lo que venian conversando antes: ").append(r).append('\n'));
        return texto.toString().strip();
    }
}
