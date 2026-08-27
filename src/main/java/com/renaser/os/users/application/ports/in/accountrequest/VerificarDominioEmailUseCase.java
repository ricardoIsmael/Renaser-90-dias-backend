package com.renaser.os.users.application.ports.in.accountrequest;

/**
 * ¿El dominio de este correo puede recibir correo? Porte de {@code verificarDominioCorreo}
 * (AR-05 del repo viejo). Se consultan los registros MX del dominio: es lo maximo que se puede
 * saber sin enviar nada ni pagar a un tercero, y cubre el error que de verdad ocurre — el
 * dominio mal escrito ({@code @gmial.cm}, dominios inventados). Un dominio sin MX no recibe
 * correo de nadie, asi que una direccion ahi esta muerta con certeza.
 *
 * <p><b>Lo que NO prueba:</b> que el buzon exista. {@code noexiste@renaser.com} y
 * {@code darren@renaser.com} dan el mismo resultado. Eso lo prueba el codigo de verificacion
 * (ver {@code EnviarCodigoVerificacionEmailUseCase}), que es justo el paso siguiente del
 * formulario; el MX va DELANTE para no gastar un envio real en un dominio muerto.
 *
 * <p><b>Nunca se hace una sonda SMTP</b> (conectarse y probar {@code RCPT TO}): la mitad de los
 * servidores la responden mal a proposito y la otra mitad acaba metiendo la IP en listas negras.
 *
 * <p>El resultado es un AVISO, no una condicion: el formulario deja continuar igual
 * (decision 2026-08-01) porque ninguna verificacion es perfecta y un admin revisa cada solicitud.
 */
public interface VerificarDominioEmailUseCase {

    ResultadoVerificacionDominio verificar(String email);

    /** Por que un dominio no puede recibir correo. */
    enum MotivoNoEntregable {
        /** El dominio existe pero no declara ningun MX. */
        SIN_MX,
        /** El DNS dice que el dominio no existe. */
        DOMINIO_INEXISTENTE,
        /** Ni siquiera es un correo bien formado, no hay dominio que consultar. */
        FORMATO
    }

    /**
     * Tres estados, no dos. {@code entregable == null} es "no se pudo averiguar" (DNS lento, sin
     * salida a red) y NO se convierte en un "no": el formulario avisa y deja seguir.
     *
     * @param motivo solo tiene valor cuando {@code entregable} es {@code false}.
     */
    record ResultadoVerificacionDominio(Boolean entregable, MotivoNoEntregable motivo) {

        public static ResultadoVerificacionDominio puedeRecibir() {
            return new ResultadoVerificacionDominio(true, null);
        }

        public static ResultadoVerificacionDominio noPuedeRecibir(MotivoNoEntregable motivo) {
            return new ResultadoVerificacionDominio(false, motivo);
        }

        /** Ni si ni no: el formulario avisa y deja continuar. */
        public static ResultadoVerificacionDominio noSeSabe() {
            return new ResultadoVerificacionDominio(null, null);
        }
    }
}
