package com.villagebooker.mail;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Replaces Resend in every test (@Primary), also when .env has an API key: no email leaves a test.
// Remembers what was sent; failWith makes the next sends fail.
@Primary
@Component
public class FakeEmailSender implements EmailSender {

    public record Sent(String to, String subject, String text) {
    }

    private final List<Sent> sent = Collections.synchronizedList(new ArrayList<>());
    private volatile EmailSendException failure;

    @Override
    public void send(String to, String subject, String text) {
        if (failure != null) {
            throw failure;
        }
        sent.add(new Sent(to, subject, text));
    }

    public List<Sent> sent() {
        return sent;
    }

    public void failWith(EmailSendException failure) {
        this.failure = failure;
    }

    public void reset() {
        sent.clear();
        failure = null;
    }
}
