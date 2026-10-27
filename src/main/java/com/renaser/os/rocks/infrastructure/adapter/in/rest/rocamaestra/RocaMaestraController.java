package com.renaser.os.rocks.infrastructure.adapter.in.rest.rocamaestra;

import com.renaser.os.rocks.application.ports.in.rocamaestra.ConsultarRocasMaestrasUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase;
import com.renaser.os.rocks.application.ports.in.rocamaestra.DefinirRocaMaestraUseCase.DefinirRocaMaestraCommand;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MapaPendienteException;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraFijaException;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/v1/rocks/master")
@Funcionalidad("plan")
public class RocaMaestraController {

    private final ConsultarRocasMaestrasUseCase consultarUseCase;
    private final DefinirRocaMaestraUseCase definirUseCase;

    public RocaMaestraController(ConsultarRocasMaestrasUseCase consultarUseCase,
                                  DefinirRocaMaestraUseCase definirUseCase) {
        this.consultarUseCase = consultarUseCase;
        this.definirUseCase = definirUseCase;
    }

    @RequiresPermission(Permission.FOLLOW_OWN_PROGRAM)
    @GetMapping
    public List<RocaMaestraResponse> listar(@ActorAutenticado UserId actor) {
        return consultarUseCase.misRocasMaestras(actor).stream()
                .map(RocaMaestraResponse::from)
                .toList();
    }

    /**
     * Define el objetivo de 90 dias de ese eje, o registra su avance si ya estaba definido.
     *
     * <p>Con el Mapa de Renacimiento ACTIVO, el objetivo, la meta, la unidad y la linea base quedan fijos
     * (D-234): cambiarlos responde 409 {@code ROCA_MAESTRA_FIJA}. Mandar lo mismo, o lo mismo con otro
     * avance, responde 200.
     *
     * <p>Con el Mapa SIN activar (D-284), lo que escribe la activacion del Mapa ({@code "origen": "MAPA"})
     * reemplaza a la roca que hubiera. Y, con {@code renaser.rocks.exigir-mapa-activo} encendido, un pedido
     * que no viene del Mapa y cambia algo responde 409 {@code MAPA_PENDIENTE}.
     *
     * > <b>Corregido 2026-09-30 (D-234).</b> Decia: "Define el objetivo de 90 dias de ese eje, o
     * > corrige el que ya estaba". Ya no se corrige.
     * > <b>Corregido 2026-10-27 (D-284).</b> Decia que quedaba fijo una vez definido: ahora, una vez que el
     * > Mapa esta activo.
     *
     * <p>{@code PUT} y no {@code POST} porque es idempotente por diseno: por eje hay una sola
     * Roca Maestra ({@code UNIQUE (participante_id, eje)}), asi que mandar dos veces lo mismo
     * deja lo mismo. El eje va en la ruta y el dueno sale de la sesion; ninguno de los dos se
     * acepta desde el cuerpo.
     */
    @RequiresPermission(value = Permission.FOLLOW_OWN_PROGRAM, scope = "solo sobre las rocas del propio aprendiz")
    @PutMapping("/{eje}")
    public RocaMaestraResponse definir(@ActorAutenticado UserId actor, @PathVariable EjeObjetivo eje,
                                        @Valid @RequestBody DefinirRocaMaestraRequest request) {
        return RocaMaestraResponse.from(definirUseCase.definir(new DefinirRocaMaestraCommand(
                actor, eje, request.objetivo(), request.meta(), request.avance(), request.unidad(), request.lineaBase(),
                request.desdeElMapa())));
    }

    /** El 409 con su codigo, para que la app lo distinga sin adivinar por el texto (D-284). */
    @ExceptionHandler(MapaPendienteException.class)
    public ResponseEntity<RocaMaestraFijaResponse> mapaPendiente(MapaPendienteException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body(new RocaMaestraFijaResponse(MapaPendienteException.CODIGO, e.getMessage(), Instant.now()));
    }

    /** El 409 con su codigo, para que la app lo distinga sin adivinar por el texto (D-234). */
    @ExceptionHandler(RocaMaestraFijaException.class)
    public ResponseEntity<RocaMaestraFijaResponse> rocaFija(RocaMaestraFijaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body(new RocaMaestraFijaResponse(RocaMaestraFijaException.CODIGO, e.getMessage(), Instant.now()));
    }
}
