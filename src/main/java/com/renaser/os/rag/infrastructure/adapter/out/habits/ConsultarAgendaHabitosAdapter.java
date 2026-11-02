package com.renaser.os.rag.infrastructure.adapter.out.habits;

import com.renaser.os.habits.api.AgendaDelDiaFinder;
import com.renaser.os.habits.api.CategoriasDeLaFormacionFinder;
import com.renaser.os.habits.api.HabitoEnJuegoResumen;
import com.renaser.os.rag.application.ports.out.habitos.ConsultarAgendaHabitosPort;
import com.renaser.os.rag.domain.model.habitopersonal.DimensionDelHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementa {@link ConsultarAgendaHabitosPort} delegando en el contrato publico de
 * {@code habits} (D-41) — {@code rag} nunca consulta {@code registros_habito} de frente. Mismo
 * patron exacto que {@code LeerEntradasDiarioAdapter}.
 *
 * <p>Es una traduccion y nada mas: que dia es hoy para el aprendiz, cuanto paga cada habito y si
 * todavia se puede entregar lo resuelve {@code habits}, que es donde viven esas reglas.
 */
@Component
class ConsultarAgendaHabitosAdapter implements ConsultarAgendaHabitosPort {

    private final AgendaDelDiaFinder agendaDelDiaFinder;
    private final CategoriasDeLaFormacionFinder categorias;

    ConsultarAgendaHabitosAdapter(AgendaDelDiaFinder agendaDelDiaFinder, CategoriasDeLaFormacionFinder categorias) {
        this.agendaDelDiaFinder = agendaDelDiaFinder;
        this.categorias = categorias;
    }

    @Override
    public List<HabitoDelDia> deHoyDe(UserId participanteId) {
        Map<String, String> nombres = nombresDeLasCategorias(participanteId);
        return agendaDelDiaFinder.deHoyDe(participanteId).stream()
                .map(resumen -> aHabitoDelDia(resumen, nombres))
                .toList();
    }

    /**
     * Spec 038: el nombre de cada categoria es el de la formacion de la persona (Cuerpo, Mente... o las que Administracion cree en el Diplomado y
     * la Certificacion). Antes solo se conocian las cuatro de la Escuela y una categoria propia salia como «Sin dimension». Si no se puede leer,
     * quedan las cuatro de siempre: un rotulo de menos no vale una herramienta caida.
     */
    private Map<String, String> nombresDeLasCategorias(UserId participanteId) {
        try {
            return categorias.activasDe(participanteId).stream()
                    .collect(Collectors.toMap(CategoriasDeLaFormacionFinder.CategoriaResumen::clave,
                            CategoriasDeLaFormacionFinder.CategoriaResumen::nombre, (una, otra) -> una));
        } catch (RuntimeException sinLeer) {
            return Map.of();
        }
    }

    @Override
    public int completar(UserId actorId, UUID registroId) {
        return agendaDelDiaFinder.completar(actorId, registroId);
    }

    @Override
    public ZoneId zonaDe(UserId participanteId) {
        return agendaDelDiaFinder.zonaDe(participanteId);
    }

    private static HabitoDelDia aHabitoDelDia(HabitoEnJuegoResumen resumen, Map<String, String> nombres) {
        return new HabitoDelDia(resumen.registroId(), resumen.titulo(), resumen.estado(), resumen.puntosEnJuego(),
                resumen.puntosMaximos(), resumen.plazo(), resumen.exigeEvidencia(),
                resumen.tramos().stream().map(ConsultarAgendaHabitosAdapter::aTramo).toList(), resumen.claveSistema(),
                resumen.tituloDelPrograma(), resumen.horaInicio(),
                dimensionDe(resumen.categoriaClave(), nombres), resumen.habitoId(), resumen.fecha());
    }

    private static String dimensionDe(String categoriaClave, Map<String, String> nombres) {
        if (categoriaClave == null) {
            return null;
        }
        String propia = nombres.get(categoriaClave);
        return propia != null ? propia : DimensionDelHabito.deClave(categoriaClave).map(DimensionDelHabito::etiqueta).orElse(null);
    }

    private static TramoPuntos aTramo(HabitoEnJuegoResumen.TramoPuntos tramo) {
        return new TramoPuntos(tramo.hasta(), tramo.puntos());
    }
}
