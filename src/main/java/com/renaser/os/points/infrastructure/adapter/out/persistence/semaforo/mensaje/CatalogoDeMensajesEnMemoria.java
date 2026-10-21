package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo.mensaje;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.github.benmanes.caffeine.cache.Ticker;
import com.renaser.os.points.application.ports.out.semaforo.mensaje.CatalogoDeMensajesPort;
import com.renaser.os.points.domain.model.semaforo.mensaje.GrupoDelMensaje;
import com.renaser.os.points.domain.model.semaforo.mensaje.MensajeDelSemaforo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El catálogo de mensajes del semáforo en memoria (D-277, SC-004). Son ~115 filas que solo cambian con una migración o
 * una corrección a mano, y leerlas en cada {@code /home} costaba hasta dos consultas por lectura. Se leen todas de una
 * vez, la primera vez que alguien las pide, y se vuelven a leer cada {@value #MINUTOS_DE_VIGENCIA} minutos o cuando
 * se pide {@link #recargar()}. Una corrección a mano tarda, como mucho, ese tiempo en verse en cada servidor.
 *
 * <p>La lectura es atómica: si varias lecturas de {@code /home} llegan con la copia vencida, una sola va a la base y
 * las demás esperan su resultado. Si la lectura falla, no se guarda nada y el error llega a quien preguntó
 * ({@code /home} sale sin mensaje esa vez); la próxima consulta lo reintenta.
 */
@Component
class CatalogoDeMensajesEnMemoria implements CatalogoDeMensajesPort {

    static final int MINUTOS_DE_VIGENCIA = 10;
    private static final String UNICA = "catalogo";

    private final LoadingCache<String, Copia> copia;

    @Autowired
    CatalogoDeMensajesEnMemoria(LectorDelCatalogo lector) {
        this(lector, Ticker.systemTicker());
    }

    CatalogoDeMensajesEnMemoria(LectorDelCatalogo lector, Ticker reloj) {
        this.copia = Caffeine.newBuilder()
                .expireAfterWrite(Duration.ofMinutes(MINUTOS_DE_VIGENCIA))
                .ticker(reloj)
                .executor(Runnable::run)
                .build(clave -> new Copia(lector.todos()));
    }

    @Override
    public List<MensajeDelSemaforo> activosDe(GrupoDelMensaje grupo) {
        return copia.get(UNICA).activosPorGrupo().get(grupo);
    }

    @Override
    public Optional<MensajeDelSemaforo> porId(String id) {
        return Optional.ofNullable(copia.get(UNICA).porId().get(id));
    }

    @Override
    public void recargar() {
        copia.invalidateAll();
    }

    /** El catálogo ya indexado de las dos formas en que se consulta. */
    private record Copia(Map<String, MensajeDelSemaforo> porId, Map<GrupoDelMensaje, List<MensajeDelSemaforo>> activosPorGrupo) {

        Copia(List<MensajeDelSemaforo> todos) {
            this(indicePorId(todos), indicePorGrupo(todos));
        }

        private static Map<String, MensajeDelSemaforo> indicePorId(List<MensajeDelSemaforo> todos) {
            Map<String, MensajeDelSemaforo> indice = new HashMap<>();
            todos.forEach(mensaje -> indice.put(mensaje.id(), mensaje));
            return Map.copyOf(indice);
        }

        /** Todos los grupos tienen lista, aunque esté vacía; cada una ordenada por id. */
        private static Map<GrupoDelMensaje, List<MensajeDelSemaforo>> indicePorGrupo(List<MensajeDelSemaforo> todos) {
            Map<GrupoDelMensaje, List<MensajeDelSemaforo>> indice = new EnumMap<>(GrupoDelMensaje.class);
            for (GrupoDelMensaje grupo : GrupoDelMensaje.values()) {
                indice.put(grupo, todos.stream().filter(m -> m.grupo() == grupo && m.activo())
                        .sorted(Comparator.comparing(MensajeDelSemaforo::id)).toList());
            }
            return Map.copyOf(indice);
        }
    }
}
