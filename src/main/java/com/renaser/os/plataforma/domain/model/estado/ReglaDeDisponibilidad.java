package com.renaser.os.plataforma.domain.model.estado;

import java.util.List;
import java.util.Optional;

/**
 * Si una funcionalidad prendida le corresponde a esta persona (spec 002, fase 2). La implementan el despliegue
 * progresivo (está dentro del porcentaje o es probadora) y los roles personalizados (su rol la incluye). Una regla que
 * la deja fuera no la «apaga»: la vuelve <b>no disponible</b>, y la app la esconde (decisión del dueño, P1).
 *
 * <p>Orden de la evaluación: apagado manual &gt; circuito de la IA &gt; reglas de disponibilidad. Las reglas solo se
 * preguntan si la cadena está prendida, y solo para una persona: un proceso sin actor no las ve.
 *
 * <p>Cada regla se registra como bean de Spring en {@code plataforma} (con {@code @Order} si importa cuál responde
 * primero). Tiene que responder desde memoria, sin ir a la base por pedido: se evalúa en cada llamada a la API y unas 55
 * veces por cada {@code GET /funcionalidades/mias}.
 */
public interface ReglaDeDisponibilidad {

    /**
     * @param cadena las claves desde la raíz hasta la pedida ({@code ["chat", "chat.audio"]})
     * @param actor  quién pide; nunca {@code null}
     * @return la clave del nivel que la deja fuera para esta persona, o vacío si le corresponde
     */
    Optional<String> noDisponibleEn(List<String> cadena, ActorEvaluado actor);
}
