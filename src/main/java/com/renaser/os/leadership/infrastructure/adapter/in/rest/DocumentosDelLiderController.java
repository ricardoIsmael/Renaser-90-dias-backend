package com.renaser.os.leadership.infrastructure.adapter.in.rest;

import com.renaser.os.leadership.application.ports.in.GenerarDocumentoSemanalUseCase;
import com.renaser.os.leadership.application.ports.in.GenerarDocumentoSemanalUseCase.DocumentoEntregado;
import com.renaser.os.leadership.application.ports.in.GenerarDocumentoSemanalUseCase.PedidoDeDocumento;
import com.renaser.os.leadership.application.ports.in.GenerarDocumentoSemanalUseCase.PedidoDeDocumentoDelMes;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * «Descargar semana» (spec 056, FR-032 y FR-035 a FR-041): genera el documento de una semana en el servidor, lo guarda en el almacenamiento
 * privado y entrega un enlace que vence a los 10 minutos; si ya existe con los mismos datos, lo reutiliza.
 * <ul>
 *   <li>{@code POST /api/v1/leadership/people/{personId}/documento} {@code {tipo: APRENDIZ|MENTOR, semanaHasta?}} — el de una persona. Lo piden
 *       el liderazgo y, para un aprendiz, el mentor que lo acompaña hoy (FR-047).</li>
 *   <li>{@code POST …/mentors/{mentorId}/documento} {@code {semanaHasta?}} — el de un mentor: solo el liderazgo.</li>
 *   <li>{@code POST …/report/documento} {@code {month}} — el reporte del mes: solo el liderazgo.</li>
 * </ul>
 *
 * <p>El permiso declarado no protege nada: MENTOR pasa el interceptor sin que se le mire (A-1) y MENTOR_LEAD está en modo sombra. Quien decide es el
 * servicio, por relación (FR-041). El de personas declara {@code USE_APP}, el permiso con el que el mentor llega a la ficha de sus aprendices
 * (spec 055), para que el cumplimiento de la matriz de Mentor no le cierre la puerta. Responde {@code no-store}: el enlace es privado.
 */
@RestController
@RequestMapping("/api/v1/leadership")
@Funcionalidad("lider.cumplimiento")
public class DocumentosDelLiderController {

    private final GenerarDocumentoSemanalUseCase documentos;

    public DocumentosDelLiderController(GenerarDocumentoSemanalUseCase documentos) {
        this.documentos = documentos;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "AccesoAlDocumento: MENTOR_LEAD/ADMIN/ALCHEMIST activo de la formación de la persona, "
            + "o el MENTOR que acompaña hoy al aprendiz (NO_ACOMPANA si no); el permiso no alcanza (A-1)")
    @PostMapping("/people/{personId}/documento")
    public ResponseEntity<DocumentoEntregadoResponse> deUnaPersona(@ActorAutenticado UserId actorId, @PathVariable UUID personId,
                                                                    @Valid @RequestBody PedirDocumentoDePersonaRequest cuerpo) {
        PedidoDeDocumento pedido = new PedidoDeDocumento(actorId, personId, cuerpo.semanaHasta());
        DocumentoEntregado entregado = cuerpo.tipo() == PedirDocumentoDePersonaRequest.Tipo.MENTOR
                ? documentos.delMentor(pedido) : documentos.delAprendiz(pedido);
        return sinCache(entregado);
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_CORPS,
            scope = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; el mentor, de una formación que quien pide ve; el permiso no alcanza (A-1)")
    @PostMapping("/mentors/{mentorId}/documento")
    public ResponseEntity<DocumentoEntregadoResponse> deUnMentor(@ActorAutenticado UserId actorId, @PathVariable UUID mentorId,
                                                                  @Valid @RequestBody(required = false) PedirDocumentoDeMentorRequest cuerpo) {
        LocalDate semanaHasta = cuerpo == null ? null : cuerpo.semanaHasta();
        return sinCache(documentos.delMentor(new PedidoDeDocumento(actorId, mentorId, semanaHasta)));
    }

    @RequiresPermission(value = Permission.VIEW_MENTOR_REPORT,
            scope = "AccesoDeLiderazgo: MENTOR_LEAD/ADMIN/ALCHEMIST activo; los mentores de sus formaciones; el permiso no alcanza (A-1)")
    @PostMapping("/report/documento")
    public ResponseEntity<DocumentoEntregadoResponse> delMes(@ActorAutenticado UserId actorId,
                                                              @Valid @RequestBody PedirDocumentoDelMesRequest cuerpo) {
        return sinCache(documentos.delMes(new PedidoDeDocumentoDelMes(actorId, cuerpo.month())));
    }

    private static ResponseEntity<DocumentoEntregadoResponse> sinCache(DocumentoEntregado entregado) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(DocumentoEntregadoResponse.from(entregado));
    }
}
