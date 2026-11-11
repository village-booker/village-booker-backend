package com.villagebooker.mail;

import com.resend.Resend;
import com.resend.core.exception.ResendException;
import com.resend.services.emails.model.CreateEmailOptions;
import com.villagebooker.config.MailProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnExpression("!'${app.mail.resend-api-key:}'.isBlank()")
public class ResendEmailSender implements EmailSender {

    private final Resend resend;
    private final String from;

    public ResendEmailSender(MailProperties properties) {
        this.resend = new Resend(properties.resendApiKey());
        this.from = properties.from();
    }

    @Override
    public void send(String to, String subject, String text) {
        CreateEmailOptions email = CreateEmailOptions.builder()
                .from(from)
                .to(to)
                .subject(subject)
                .text(text)
                .build();
        try {
            resend.emails().send(email);
        } catch (ResendException e) {
            throw new EmailSendException("Resend refused the email (" + e.getStatusCode() + "): " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new EmailSendException("Resend could not be reached: " + e.getMessage(), e);
        }
    }
}
