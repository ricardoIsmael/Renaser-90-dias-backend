package com.renaser.os.habits.infrastructure.adapter.in.rest.carrera;

import com.renaser.os.habits.application.ports.in.carrera.ConsultarRutaPropiaUseCase;
import com.renaser.os.habits.application.ports.in.carrera.RegistrarCarreraUseCase;
import com.renaser.os.habits.application.ports.in.carrera.RegistrarCarreraUseCase.CarreraRegistrada;
import com.renaser.os.habits.application.ports.in.carrera.RegistrarCarreraUseCase.CarreraYaRegistradaException;
import com.renaser.os.habits.application.ports.in.carrera.RegistrarCarreraUseCase.RegistrarCarreraCommand;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.ApiErrorConCodigo;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.NoSuchElementException;
import java.util.UUID;

/**
 * Spec 042 (D-323): la carrera con GPS de la propia persona. {@code POST /api/v1/carreras} es la ÚNICA escritura de una carrera
 * (idempotente por {@code carreraId}: 201 la primera vez, 200 con {@code repetida: true} si se repite igual, 409
 * {@code CARRERA_YA_REGISTRADA} si llega otro contenido con la misma clave). {@code GET /api/v1/carreras/{carreraId}/ruta} lee la
 * ruta propia. La de otra persona se lee desde la ficha del mentor y la del líder, cada una con su guardián.
 *
 * <p>Interruptor: {@code habitos.km.carreras}, hija de {@code habitos.km}, hija de {@code habitos}: apagar cualquiera responde
 * 503 y la app deja la carrera en su cola. El controller es tonto: valida la forma e invoca UN caso de uso.
 */
@RestController
@RequestMapping("/api/v1/carreras")
@Funcionalidad("habitos.km.carreras")
public class CarreraController {

    private final RegistrarCarreraUseCase registrar;
    private final ConsultarRutaPropiaUseCase rutaPropia;

    public CarreraController(RegistrarCarreraUseCase registrar, ConsultarRutaPropiaUseCase rutaPropia) {
        this.registrar = registrar;
        this.rutaPropia = rutaPropia;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "la propia persona: el participante es quien esta autenticado")
    @PostMapping
    public ResponseEntity<CarreraResponse> registrar(@ActorAutenticado UserId actor, @RequestBody @Valid CarreraRequest request) {
        CarreraRegistrada registrada = registrar.registrar(new RegistrarCarreraCommand(actor, request.aDatos(), request.rutaEnviada()));
        return ResponseEntity.status(registrada.repetida() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(CarreraResponse.from(registrada));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = "la propia persona: solo sus carreras (otra persona es 404)")
    @GetMapping("/{carreraId}/ruta")
    public ResponseEntity<RutaDeCarreraResponse> ruta(@ActorAutenticado UserId actor, @PathVariable UUID carreraId) {
        var ruta = rutaPropia.rutaPropia(actor, carreraId)
                .orElseThrow(() -> new NoSuchElementException("Esta carrera no tiene una ruta guardada."));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new RutaDeCarreraResponse(ruta.polyline(), ruta.puntos()));
    }

    @ExceptionHandler(CarreraYaRegistradaException.class)
    public ResponseEntity<ApiErrorConCodigo> yaRegistrada(CarreraYaRegistradaException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT).contentType(MediaType.APPLICATION_JSON)
                .body(ApiErrorConCodigo.of(e.getMessage(), CarreraYaRegistradaException.CODIGO, null));
    }
}
