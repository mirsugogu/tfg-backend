package com.optima.api.common.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Servicio para enviar correos electronicos
 * Si el servidor de correo falla, no pasa nada, la operacion principal sigue funcionando
 */
@Service
@RequiredArgsConstructor
public class MailService {

    private final JavaMailSender mailSender;

    // el email desde el que se envian los correos, se configura en application.properties
    @Value("${app.mail.from}")
    private String from;

    /**
     * Envia un correo simple con asunto y cuerpo de texto
     */
    public void sendSimpleEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);

        try {
            mailSender.send(message);
        } catch (MailException ignored) {
            // si falla el envio no hacemos nada, el negocio no se debe romper por un correo
        }
    }
}
