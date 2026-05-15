package com.optima.api.common.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Servicio transversal de envio de correo. Envuelve JavaMailSender
 * (autoconfigurado por Spring Boot leyendo spring.mail.* de
 * application.properties) para que el resto del proyecto pueda
 * mandar emails sin tocar la API de bajo nivel.
 *
 * Es best-effort: si el envio falla (Gmail caido, credenciales
 * mal, red rota...) loguea un warning y vuelve sin lanzar excepcion.
 * Asi un fallo de email NUNCA bloquea el flujo principal (un registro de
 * negocio no debe fallar porque Gmail este caido).
 *
 * COMUNICACION:
 * - Lo inyectara cualquier service que necesite mandar correo
 *   (p.ej. AuthService cuando se implemente el auto-registro de negocio,
 *   o un futuro AppointmentService para notificar citas).
 * - Llama a (red externa): smtp.gmail.com:587 via JavaMailSender + JavaMail.
 *
 * Lectura de configuracion (application.properties):
 *   spring.mail.host                smtp.gmail.com
 *   spring.mail.port                587 (STARTTLS)
 *   spring.mail.username            MAIL_USERNAME (env var)
 *   spring.mail.password            MAIL_PASSWORD (App Password 16 chars)
 *   app.mail.from                   remitente que ve el destinatario
 *
 * Por que es best-effort: el envio sincrono a SMTP puede tardar segundos
 * o fallar por causas externas. Bloquear o romper la transaccion principal
 * por culpa del correo es mal diseno. Si en el futuro se requiere garantia
 * de entrega, lo correcto es introducir una cola (outbox pattern) en lugar
 * de propagar la excepcion.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String from;

    /**
     * Envia un correo de texto plano. Best-effort: cualquier fallo del
     * proveedor SMTP se loguea pero no se propaga al llamador.
     *
     * @param to      destinatario (validado por Jakarta @Email en el caller).
     * @param subject asunto. No puede ser null ni vacio (no validamos aqui).
     * @param body    cuerpo plano. Para HTML usar otro metodo en el futuro.
     */
    public void sendSimpleEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
            log.info("Email enviado a {} con asunto '{}'", to, subject);
        } catch (MailException ex) {
            log.warn("Fallo al enviar email a {} con asunto '{}': {}",
                    to, subject, ex.getMessage());
        }
    }
}
