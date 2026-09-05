package com.renaser.os.habits.infrastructure.adapter.in.rest.registro;

import com.renaser.os.evidence.api.TipoEvidencia;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.CompletarRegistroUseCase.CompletarRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase;
import com.renaser.os.habits.application.ports.in.registro.SolicitarUrlEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.SolicitarUrlEvidenciaRegistroUseCase.SolicitarUrlEvidenciaRegistroCommand;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase;
import com.renaser.os.habits.application.ports.in.registro.SubirEvidenciaRegistroUseCase.SubirEvidenciaRegistroCommand;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Actor resuelto desde la sesion por {@code @ActorAutenticado}, con respaldo por el header
 * temporal `X-Actor-Id` (D-29 de `users`) mientras dure la migracion.
 * Autoservicio: el participante solo opera sobre sus propios tracks.
 */
@RestController
@RequestMapping("/api/v1/habit-tracks")
public class HabitTrackController {

    private final ConsultarTracksDelDiaConCatalogoUseCase consultarTracksDelDiaUseCase;
    private final CompletarRegistroUseCase completarRegistroUseCase;
    private final SubirEvidenciaRegistroUseCase subirEvidenciaUseCase;
    private final SolicitarUrlEvidenciaRegistroUseCase urlEvidenciaUseCase;

    public HabitTrackController(ConsultarTracksDelDiaConCatalogoUseCase consultarTracksDelDiaUseCase,
                                 CompletarRegistroUseCase completarRegistroUseCase,
                                 SubirEvidenciaRegistroUseCase subirEvidenciaUseCase,
                                 SolicitarUrlEvidenciaRegistroUseCase urlEvidenciaUseCase) {
        this.consultarTracksDelDiaUseCase = consultarTracksDelDiaUseCase;
        this.completarRegistroUseCase = completarRegistroUseCase;
        this.subirEvidenciaUseCase = subirEvidenciaUseCase;
        this.urlEvidenciaUseCase = urlEvidenciaUseCase;
    }

    /**
     * Hueco #10: cada registro trae el catalogo resuelto (titulo/tipo/guia/horario) — sin N+1.
     * Desde 2026-09-05 tambien los puntos en juego y el plazo de entrega.
     *
     * <p><b>Corregido 2026-09-05 (E-105).</b> Esta linea era
     * {@code consultar(actor, actor, LocalDate.now())}: la fecha del SERVIDOR. Con el proceso en
     * UTC y el padron en America/Lima (UTC-5), desde las 19:00 hora local pedia los registros de
     * MANANA y devolvia lista vacia — la pantalla de habitos se apagaba todas las noches, justo
     * en la franja de mayor uso. Cual es "hoy" para una persona depende de su zona, asi que la
     * decision se movio al caso de uso ({@code consultarHoyDe}) y este controller volvio a ser
     * tonto: regla 01, nada de calculos ni de reloj en un adaptador de transporte.
     */
    @RequiresPermission(value = Permission.USE_APP, scope = "opera sobre los habitos del propio actor")
    @GetMapping("/today")
    public List<RegistroHabitoConCatalogoResponse> hoy(@ActorAutenticado UserId actor) {
        return consultarTracksDelDiaUseCase.consultarHoyDe(actor)
                .stream().map(RegistroHabitoConCatalogoResponse::from).toList();
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "dueno del registro de habito")
    @PostMapping("/{id}/complete")
    public RegistroHabitoResponse completar(@ActorAutenticado UserId actor, @PathVariable String id,
                                             @RequestBody @Valid CompletarRegistroRequest request) {
        var registro = completarRegistroUseCase.completar(new CompletarRegistroCommand(actor,
                RegistroHabitoId.of(java.util.UUID.fromString(id)), request.respuestaTexto(),
                request.calificacionProductividad()));
        return RegistroHabitoResponse.from(registro);
    }

    /**
     * Paso 1 del camino generico de evidencia: URL PUT prefirmada para FOTO/VIDEO/AUDIO/CAPTURA.
     * El TEXTO no pasa por aca — va directo al POST de abajo con `contenidoTexto`.
     */
    @RequiresPermission(value = Permission.USE_APP, scope = "dueno del registro de habito")
    @PostMapping("/{id}/evidence/upload-url")
    public UrlEvidenciaRegistroResponse urlDeSubidaEvidencia(@ActorAutenticado UserId actor,
                                                               @PathVariable String id,
                                                               @RequestBody @Valid
                                                               SolicitarUrlEvidenciaRegistroRequest request) {
        var url = urlEvidenciaUseCase.solicitarUrl(new SolicitarUrlEvidenciaRegistroCommand(actor,
                RegistroHabitoId.of(java.util.UUID.fromString(id)), request.tipoContenido()));
        return UrlEvidenciaRegistroResponse.from(url);
    }

    /** D-H6: sube la evidencia de un registro diario, delegando en `evidence.api.RegistrarEvidenciaPort`. */
    @RequiresPermission(value = Permission.USE_APP, scope = "dueno del registro de habito")
    @PostMapping("/{id}/evidence")
    public EvidenciaRegistroResponse subirEvidencia(@ActorAutenticado UserId actor,
                                                      @PathVariable String id,
                                                      @RequestBody @Valid SubirEvidenciaRegistroRequest request) {
        var evidencia = subirEvidenciaUseCase.subir(new SubirEvidenciaRegistroCommand(actor,
                RegistroHabitoId.of(java.util.UUID.fromString(id)), TipoEvidencia.valueOf(request.tipo()),
                request.bucket(), request.rutaStorage(), request.contenidoTexto(), request.timestampExif(),
                request.gpsLat(), request.gpsLng()));
        return EvidenciaRegistroResponse.from(evidencia);
    }
}
