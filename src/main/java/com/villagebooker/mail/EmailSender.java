package com.villagebooker.mail;

// Sends one plain-text email; called outside database transactions. Exists only when app.mail.resend-api-key is
// set (ResendEmailSender); tests replace it with FakeEmailSender.
public interface EmailSender {

    void send(String to, String subject, String text);
}
