package com.renaser.os.shared.web;

import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.web.security.PublicEndpoint;
import com.renaser.os.shared.web.security.RequiresPermission;
import io.swagger.v3.oas.models.Operation;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.method.HandlerMethod;

import static org.assertj.core.api.Assertions.assertThat;

/** Lo que {@link OpenApiConfig} agrega a cada operacion, sin levantar Spring. */
class OpenApiConfigTest {

    @RequiresPermission(Permission.USE_APP)
    static class EjemploController {
        @PublicEndpoint("el login no puede exigir sesion")
        public void publico() { }

        public void delControllerConPermiso() { }
    }

    static class SinAnotaciones {
        public void nada() { }
    }

    private static Operation procesar(Object bean, String metodo) throws NoSuchMethodException {
        HandlerMethod handler = new HandlerMethod(bean, bean.getClass().getMethod(metodo));
        return new OpenApiConfig().etiquetaPermisoYSesion().customize(new Operation(), handler);
    }

    @Test
    @DisplayName("una ruta @PublicEndpoint no exige la sesion y dice por que")
    void publicaSinSesion() throws Exception {
        Operation op = procesar(new EjemploController(), "publico");
        assertThat(op.getSecurity()).isEmpty();
        assertThat(op.getDescription()).contains("Pública").contains("el login no puede exigir sesion");
    }

    @Test
    @DisplayName("el permiso del controller se anota en la descripcion y en x-permiso")
    void permisoDelController() throws Exception {
        Operation op = procesar(new EjemploController(), "delControllerConPermiso");
        String permiso = Permission.USE_APP.name();
        assertThat(op.getExtensions()).containsEntry("x-permiso", permiso);
        assertThat(op.getDescription()).contains(permiso);
        assertThat(op.getSecurity()).isNull(); // hereda el esquema global de sesion
    }

    @Test
    @DisplayName("sin anotaciones solo se etiqueta, y nada mas cambia")
    void sinAnotaciones() throws Exception {
        Operation op = procesar(new SinAnotaciones(), "nada");
        assertThat(op.getDescription()).isNull();
        assertThat(op.getExtensions()).isNull();
        assertThat(op.getTags()).hasSize(1);
    }

    @Test
    @DisplayName("la etiqueta es <modulo> · <controller sin el sufijo>")
    void etiquetaPorModulo() {
        assertThat(OpenApiConfig.moduloDe(OpenApiConfig.class)).isEqualTo("shared");
        assertThat(OpenApiConfig.moduloDe(String.class)).isEqualTo("otros");
        assertThat(OpenApiConfig.nombreCorto(EjemploController.class)).isEqualTo("Ejemplo");
        assertThat(OpenApiConfig.nombreCorto(SinAnotaciones.class)).isEqualTo("SinAnotaciones");
    }
}
