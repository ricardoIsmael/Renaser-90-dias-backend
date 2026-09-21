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
 * <p><b>Pero salir a la red, sale</b> — y por eso desde el 2026-09-21 este caso de uso cuesta
 * cupo. La consulta MX es una peticion saliente hacia un nombre que elige quien pregunta y que se
 * emite a nombre de la IP del producto: el mismo cuidado que el parrafo de arriba le tiene a esa
 * reputacion frente a SMTP hay que tenerselo frente al DNS. De ahi que la firma pida la IP de
 * quien llama — sin ella no hay a quien contarle la consulta, y este era el unico de los tres
 * endpoints publicos de correo que no la recibia.
 *
 * <p>El resultado es un AVISO, no una condicion: el formulario deja continuar igual
 * (decision 2026-08-01) porque ninguna verificacion es perfecta y un admin revisa cada solicitud.
 */
public interface VerificarDominioEmailUseCase {

    /**
     * @param requestIp IP de quien pregunta, para el limite de tasa. La consulta es publica y sin
     *                  sesion, asi que la IP es el unico sujeto al que se le puede contar.
     *                  {@code null} es "no se sabe quien llama" y entonces no se cuenta, mismo
     *                  criterio que {@link ConsultarEmailRegistradoUseCase#estaRegistrado}.
     */
    ResultadoVerificacionDominio verificar(String email, String requestIp);

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
