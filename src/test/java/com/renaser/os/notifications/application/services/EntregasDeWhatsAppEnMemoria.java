package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.out.whatsapp.EntregasDeWhatsAppPort;
import com.renaser.os.notifications.domain.model.whatsapp.EntregaDeWhatsApp;
import com.renaser.os.notifications.domain.model.whatsapp.EstadoDeEntrega;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * La tabla {@code entregas_whatsapp} en memoria, con la misma regla que la base: la clave es unica ({@code apartar} devuelve {@code false} si ya existia) y el estado solo
 * avanza. Para probar los servicios sin Spring ni base (la base de verdad la prueba {@code EntregasDeWhatsAppJdbcAdapterIT}).
 */
final class EntregasDeWhatsAppEnMemoria implements EntregasDeWhatsAppPort {

    final Map<UUID, EntregaDeWhatsApp> porId = new LinkedHashMap<>();
    int llamadasAApartar;

    @Override
    public boolean apartar(EntregaDeWhatsApp entrega) {
        llamadasAApartar++;
        if (porId.values().stream().anyMatch(e -> e.clave().equals(entrega.clave()))) {
            return false;
        }
        porId.put(entrega.id(), entrega);
        return true;
    }

    @Override
    public void guardarResultado(EntregaDeWhatsApp entrega) {
        EntregaDeWhatsApp actual = porId.get(entrega.id());
        if (actual != null && actual.estado().puedeAvanzarA(entrega.estado())) {
            porId.put(entrega.id(), entrega);
        }
    }

    @Override
    public boolean avanzarEstado(UUID id, EstadoDeEntrega nuevo, String motivo, String wamid, String proveedorId, Instant ahora) {
        EntregaDeWhatsApp actual = porId.get(id);
        if (actual == null) {
            return false;
        }
        boolean avanza = actual.estado().puedeAvanzarA(nuevo);
        porId.put(id, new EntregaDeWhatsApp(id, actual.clave(), actual.eventoId(), actual.usuarioId(), actual.avisoMin(),
                avanza ? nuevo : actual.estado(), avanza && motivo != null ? motivo : actual.motivo(),
                actual.wamid() != null ? actual.wamid() : wamid, actual.proveedorId() != null ? actual.proveedorId() : proveedorId,
                actual.creadoEn(), ahora));
        return avanza;
    }

    @Override
    public Optional<EntregaDeWhatsApp> porId(UUID id) {
        return Optional.ofNullable(porId.get(id));
    }

    @Override
    public Optional<EntregaDeWhatsApp> porProveedorId(String proveedorId) {
        return porId.values().stream().filter(e -> proveedorId.equals(e.proveedorId())).findFirst();
    }

    @Override
    public Optional<EntregaDeWhatsApp> porWamid(String wamid) {
        return porId.values().stream().filter(e -> wamid.equals(e.wamid())).findFirst();
    }

    @Override
    public long contarQueSalieron(Instant desde, Instant hasta) {
        return porId.values().stream().filter(e -> !e.creadoEn().isBefore(desde) && e.creadoEn().isBefore(hasta))
                .filter(e -> e.estado() != EstadoDeEntrega.OMITIDO && e.estado() != EstadoDeEntrega.FALLIDO).count();
    }

    @Override
    public ConteoDeEntregas conteoDelEvento(UUID eventoId) {
        Map<EstadoDeEntrega, Integer> porEstado = new EnumMap<>(EstadoDeEntrega.class);
        Set<Object> personasSinNumero = new HashSet<>();
        int omitidasSinNumero = 0;
        for (EntregaDeWhatsApp e : new ArrayList<>(porId.values())) {
            if (!e.eventoId().equals(eventoId)) {
                continue;
            }
            porEstado.merge(e.estado(), 1, Integer::sum);
            if (e.estado() == EstadoDeEntrega.OMITIDO && "Sin WhatsApp".equals(e.motivo())) {
                omitidasSinNumero++;
                personasSinNumero.add(e.usuarioId());
            }
        }
        return new ConteoDeEntregas(porEstado, omitidasSinNumero, personasSinNumero.size());
    }

    @Override
    public int purgarAnterioresA(Instant limite) {
        List<UUID> viejas = porId.values().stream().filter(e -> e.creadoEn().isBefore(limite)).map(EntregaDeWhatsApp::id).toList();
        viejas.forEach(porId::remove);
        return viejas.size();
    }

    List<EntregaDeWhatsApp> todas() {
        return List.copyOf(porId.values());
    }
}
