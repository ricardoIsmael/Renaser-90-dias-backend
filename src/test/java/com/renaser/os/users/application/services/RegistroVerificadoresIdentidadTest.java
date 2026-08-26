package com.renaser.os.users.application.services;

import com.renaser.os.users.application.ports.out.autenticacion.VerificadorIdentidadProveedor;
import com.renaser.os.users.domain.model.identidadexterna.ProveedorIdentidad;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistroVerificadoresIdentidadTest {

    private static VerificadorIdentidadProveedor verificadorPara(ProveedorIdentidad proveedor) {
        return new VerificadorIdentidadProveedor() {
            @Override
            public ProveedorIdentidad proveedor() {
                return proveedor;
            }

            @Override
            public com.renaser.os.users.application.ports.out.autenticacion.IdentidadVerificada verificar(
                    com.renaser.os.users.application.ports.out.autenticacion.CanjeCodigoCommand command) {
                throw new UnsupportedOperationException("no se usa en este test");
            }
        };
    }

    @Test
    void resuelveElAdaptadorRegistradoParaSuProveedor() {
        VerificadorIdentidadProveedor google = verificadorPara(ProveedorIdentidad.GOOGLE);
        RegistroVerificadoresIdentidad registro = new RegistroVerificadoresIdentidad(List.of(google));

        assertThat(registro.para(ProveedorIdentidad.GOOGLE)).isSameAs(google);
    }

    @Test
    void pedirUnProveedorSinAdaptadorRegistradoFalla() {
        RegistroVerificadoresIdentidad registro = new RegistroVerificadoresIdentidad(
                List.of(verificadorPara(ProveedorIdentidad.GOOGLE)));

        assertThatThrownBy(() -> registro.para(ProveedorIdentidad.APPLE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dosAdaptadoresParaElMismoProveedorFallaAlConstruirse() {
        VerificadorIdentidadProveedor uno = verificadorPara(ProveedorIdentidad.GOOGLE);
        VerificadorIdentidadProveedor otro = verificadorPara(ProveedorIdentidad.GOOGLE);

        assertThatThrownBy(() -> new RegistroVerificadoresIdentidad(List.of(uno, otro)))
                .isInstanceOf(IllegalStateException.class);
    }
}
