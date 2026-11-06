package com.renaser.os.calendar.infrastructure.adapter.in.rest.evento;

import com.renaser.os.calendar.application.ports.in.audiencia.ConvocatoriaUseCase;
import com.renaser.os.calendar.application.ports.in.audiencia.ConvocatoriaUseCase.Opciones;
import com.renaser.os.calendar.infrastructure.adapter.in.rest.evento.EventoResponse.AudienciaResponse;
import com.renaser.os.calendar.infrastructure.adapter.in.rest.evento.EventoResponse.PersonaResponse;
import com.renaser.os.shared.domain.FormacionDelContenido;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * El paso «¿Para quién es?» del formulario de eventos (spec 044, contrato §2): qué puede elegir quien llama, el buscador de personas y «Llega a N
 * personas». Quien puede qué lo decide el servicio con el rol y el alcance de cada uno (FR-002, FR-029).
 */
@RestController
@RequestMapping("/api/v1/calendar/audience")
@Funcionalidad("eventos.convocar")
class AudienciaController {

    private static final String QUIEN = "spec 044: ADMIN/ALCHEMIST, el líder, el mentor y la guía, cada uno dentro de su alcance; el aprendiz no";

    private final ConvocatoriaUseCase convocatoria;

    AudienciaController(ConvocatoriaUseCase convocatoria) {
        this.convocatoria = convocatoria;
    }

    /** Sin permiso responde {@code canCreate: false} (no 403): la app solo esconde «Crear evento». */
    @RequiresPermission(value = Permission.USE_APP, scope = "cualquier persona activa; el servicio dice canCreate segun su rol (spec 044)")
    @Funcionalidad(value = "eventos.convocar", laResuelveElCasoDeUso = true)
    @GetMapping("/options")
    public OpcionesResponse opciones(@ActorAutenticado UserId actor,
                                     @RequestParam(value = "formation", required = false) String formation) {
        return OpcionesResponse.from(convocatoria.opciones(actor, FormacionDelContenido.pedida(formation)));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = QUIEN)
    @GetMapping("/people")
    public PersonasResponse personas(@ActorAutenticado UserId actor,
                                     @RequestParam(value = "q", required = false) String q,
                                     @RequestParam(value = "page", defaultValue = "0") int page,
                                     @RequestParam(value = "size", defaultValue = "30") int size,
                                     @RequestParam(value = "formation", required = false) String formation) {
        var pagina = convocatoria.personas(actor, FormacionDelContenido.pedida(formation), q, page, size);
        return new PersonasResponse(pagina.personas().stream().map(PersonaResponse::from).toList(), pagina.pagina(), pagina.hayMas());
    }

    @RequiresPermission(value = Permission.USE_APP, scope = QUIEN)
    @PostMapping("/preview")
    public AudienciaResponse vistaPrevia(@ActorAutenticado UserId actor, @RequestBody VistaPreviaRequest request) {
        AudienciaWire audiencia = new AudienciaWire(request.parts(), false);
        return AudienciaResponse.from(convocatoria.vistaPrevia(actor, FormacionDelContenido.pedida(request.formation()),
                audiencia.aPedido().partes()));
    }

    record VistaPreviaRequest(String formation, List<AudienciaWire.ParteWire> parts) {
    }

    record PersonasResponse(List<PersonaResponse> items, int page, boolean hasMore) {
    }

    record GrupoResponse(String id, String name, int members) {
    }

    record OpcionesResponse(boolean canCreate, List<String> parts, List<String> eventTypes, String defaultPart, boolean canChooseAll,
                            Map<String, Integer> trafficLight, int myLearners, List<GrupoResponse> groups, List<PersonaResponse> staff) {

        static OpcionesResponse from(Opciones o) {
            return new OpcionesResponse(o.puedeCrear(),
                    o.partes().stream().sorted().map(AudienciaWire::toWireTipo).toList(),
                    o.tipos().stream().sorted().map(Enum::name).toList(),
                    o.porDefecto() == null ? null : AudienciaWire.toWireTipo(o.porDefecto()),
                    o.todos(),
                    colores(o.colores()),
                    o.misAlumnos(),
                    o.grupos().stream().map(g -> new GrupoResponse(g.id().toString(), g.nombre(), g.integrantes())).toList(),
                    o.personal().stream().map(PersonaResponse::from).toList());
        }

        private static Map<String, Integer> colores(Map<String, Integer> porColor) {
            Map<String, Integer> wire = new java.util.LinkedHashMap<>();
            porColor.forEach((color, n) -> wire.put(AudienciaWire.toWireColor(
                    com.renaser.os.calendar.domain.model.evento.audiencia.ColorDeAudiencia.valueOf(color)), n));
            return wire;
        }
    }
}
