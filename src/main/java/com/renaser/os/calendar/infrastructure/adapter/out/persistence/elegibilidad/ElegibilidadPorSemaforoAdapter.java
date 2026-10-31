package com.renaser.os.calendar.infrastructure.adapter.out.persistence.elegibilidad;

import com.renaser.os.calendar.application.ports.out.elegibilidad.ConsultarElegibilidadEventoPort;
import com.renaser.os.calendar.domain.model.evento.TipoEvento;
import com.renaser.os.points.api.ColorSemaforo;
import com.renaser.os.points.api.SemaforoFinder;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * La Mentoria del Alquimista la ve un aprendiz solo si su semaforo vigente esta en VERDE (D-293, decision del dueño
 * del 2026-10-30; cierra la pregunta abierta de D-213). Reemplaza a {@code ElegibilidadSegunAudienciaAdapter}, que
 * dejaba pasar a toda la audiencia declarada.
 *
 * <p><b>El mismo semaforo que Hoy le muestra a la persona.</b> Se lee por el contrato publico de {@code points}
 * ({@link SemaforoFinder#vigenteDe}: los ultimos 7 dias cerrados en la zona de la persona), el que ya usan el
 * resumen del mentor y la ficha del lider. Aca no se calcula ningun color ni se guarda nada: se pregunta cada vez,
 * asi que pasar a verde la muestra y dejar de estar en verde la esconde en la siguiente consulta.
 *
 * <p><b>Solo VERDE deja pasar.</b> Amarillo y rojo no. «Sin datos» tampoco (nunca verde por falta de datos, D-128): el
 * Dia 1 y los dias sin nada programado. Quien no tiene semaforo (no activo su programa) no sale en la respuesta
 * del finder y queda afuera: no se inventa un verde.
 *
 * <p>Solo para {@link TipoEvento#MENTORIA_ALQUIMISTA}: cualquier otro tipo no agrega criterio y pasa a todo aprendiz,
 * aunque el puerto no se le pregunta (ver {@code ReglasPorTipoEvento#requiereElegibilidad}). El personal tampoco
 * llega aca: lo deja pasar quien llama (ver {@link ConsultarElegibilidadEventoPort}).
 */
@Component
class ElegibilidadPorSemaforoAdapter implements ConsultarElegibilidadEventoPort {

    private final SemaforoFinder semaforo;

    ElegibilidadPorSemaforoAdapter(SemaforoFinder semaforo) {
        this.semaforo = semaforo;
    }

    @Override
    public boolean esElegible(UserId aprendiz, TipoEvento tipoEvento) {
        return elegiblesEntre(Set.of(aprendiz), tipoEvento).contains(aprendiz);
    }

    @Override
    public Set<UserId> elegiblesEntre(Collection<UserId> aprendices, TipoEvento tipoEvento) {
        if (tipoEvento != TipoEvento.MENTORIA_ALQUIMISTA || aprendices.isEmpty()) {
            return Set.copyOf(aprendices);
        }
        return semaforo.vigenteDe(aprendices).entrySet().stream()
                .filter(medicion -> medicion.getValue().color() == ColorSemaforo.VERDE)
                .map(Map.Entry::getKey)
                .collect(Collectors.toUnmodifiableSet());
    }
}
