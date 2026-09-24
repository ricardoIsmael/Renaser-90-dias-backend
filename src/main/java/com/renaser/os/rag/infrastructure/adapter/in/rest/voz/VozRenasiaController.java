package com.renaser.os.rag.infrastructure.adapter.in.rest.voz;

import com.renaser.os.rag.application.ports.in.voz.VozDelOrbeUseCase;
import com.renaser.os.rag.application.ports.in.voz.VozDelOrbeUseCase.AudioDelOrbe;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InterruptedIOException;
import java.util.UUID;

/**
 * La voz del orbe, en dos pasos (D-159), porque el reproductor del telefono solo sabe bajar una URL:
 *
 * <ul>
 *   <li>{@code POST /api/v1/renasia/voz} {@code {"texto"}}: <b>201</b>
 *   {@code {"audio":"/api/v1/renasia/voz/{id}"}} y el audio ya empieza a generarse; <b>204</b> sin
 *   voz del servidor (la app usa el TTS del telefono); <b>400</b> texto vacio o de mas de 400.</li>
 *   <li>{@code GET /api/v1/renasia/voz/{id}}: <b>200</b> {@code audio/wav} transmitido mientras se
 *   genera; <b>204</b> si la generacion fallo sin producir sonido; <b>404</b> si no existe, vencio o
 *   es de otra persona.</li>
 * </ul>
 *
 * <p>Vive bajo {@code /api/v1/renasia/**}, que exige sesion real ({@code SecurityConfig}). Sin
 * {@code produces}: asi un 400 sigue saliendo como el JSON de error de siempre.
 *
 * <p>Corregido 2026-09-23: el {@code POST} devolvia el WAV entero (D-157).
 */
@RestController
@RequestMapping("/api/v1/renasia/voz")
public class VozRenasiaController {

    static final MediaType AUDIO_WAV = MediaType.parseMediaType("audio/wav");
    private static final String RUTA = "/api/v1/renasia/voz/";

    private final VozDelOrbeUseCase vozDelOrbe;

    public VozRenasiaController(VozDelOrbeUseCase vozDelOrbe) {
        this.vozDelOrbe = vozDelOrbe;
    }

    @RequiresPermission(Permission.USE_APP)
    @PostMapping
    public ResponseEntity<VozPreparadaResponse> preparar(@ActorAutenticado UserId actorId,
                                                         @RequestBody @Valid SintetizarVozRequest request) {
        return vozDelOrbe.preparar(actorId, request.texto())
                .map(id -> ResponseEntity.status(HttpStatus.CREATED).body(new VozPreparadaResponse(RUTA + id)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @RequiresPermission(Permission.USE_APP)
    @GetMapping("/{id}")
    public ResponseEntity<StreamingResponseBody> escuchar(@ActorAutenticado UserId actorId, @PathVariable UUID id)
            throws InterruptedException {
        AudioDelOrbe audio = vozDelOrbe.buscar(actorId, id).orElse(null);
        if (audio == null) {
            return ResponseEntity.notFound().build();
        }
        if (!audio.tieneSonido()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok().contentType(AUDIO_WAV).body(salida -> {
            try {
                audio.escribirEn(salida);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new InterruptedIOException("Se corto la voz del orbe");
            }
        });
    }
}
