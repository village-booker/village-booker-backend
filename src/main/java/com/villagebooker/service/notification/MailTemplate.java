package com.villagebooker.service.notification;

import com.villagebooker.enums.user.Language;
import com.villagebooker.enums.notification.EmailStatus;
import com.villagebooker.model.EmailMessage;
import com.villagebooker.model.User;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Texts of the notification emails in both languages; the recipient's User.language picks one.
// {name} is a parameter; a line whose parameter is missing (no reason, no refund...) is left out.
enum MailTemplate {

    BOOKING_REQUESTED(
            "Zahtev za rezervaciju je poslat: {listing}",
            """
            poslali ste zahtev za rezervaciju oglasa „{listing}“.
            {dates}
            Broj gostiju: {guests}
            Ukupna cena: {total}

            Domaćin potvrđuje svaku rezervaciju. Javićemo vam čim odgovori, a najkasnije do {deadline}.""",
            "Booking request sent: {listing}",
            """
            you have sent a booking request for "{listing}".
            {dates}
            Guests: {guests}
            Total price: {total}

            The host confirms every booking. We will let you know as soon as they answer, by {deadline} at the latest."""),

    BOOKING_AWAITS_OWNER(
            "Nova rezervacija čeka vašu potvrdu: {listing}",
            """
            {guest} želi da rezerviše vaš oglas „{listing}“.
            {dates}
            Broj gostiju: {guests}
            Ukupna cena: {total}
            Napomena gosta: {note}

            Potvrdite ili odbijte rezervaciju do {deadline}, inače ističe.""",
            "New booking awaits your confirmation: {listing}",
            """
            {guest} wants to book your listing "{listing}".
            {dates}
            Guests: {guests}
            Total price: {total}
            Guest's note: {note}

            Confirm or decline the booking by {deadline}, otherwise it expires."""),

    BOOKING_AWAITS_PAYMENT(
            "Domaćin je potvrdio rezervaciju, uplatite kaparu: {listing}",
            """
            domaćin je potvrdio vašu rezervaciju oglasa „{listing}“.
            {dates}

            Da bi rezervacija bila potvrđena, uplatite kaparu od {deposit} do {deadline}. Posle toga rezervacija ističe.""",
            "The host accepted your booking, pay the deposit: {listing}",
            """
            the host has accepted your booking of "{listing}".
            {dates}

            To confirm the booking, pay the deposit of {deposit} by {deadline}. After that the booking expires."""),

    BOOKING_CONFIRMED(
            "Rezervacija je potvrđena: {listing}",
            """
            vaša rezervacija oglasa „{listing}“ je potvrđena.
            {dates}
            Broj gostiju: {guests}
            Ostatak za plaćanje: {due}

            Srećan put!""",
            "Your booking is confirmed: {listing}",
            """
            your booking of "{listing}" is confirmed.
            {dates}
            Guests: {guests}
            Left to pay: {due}

            Have a good trip!"""),

    BOOKING_PAID(
            "Gost je uplatio kaparu: {listing}",
            """
            {guest} je uplatio kaparu i rezervacija vašeg oglasa „{listing}“ je potvrđena.
            {dates}
            Broj gostiju: {guests}""",
            "The guest paid the deposit: {listing}",
            """
            {guest} has paid the deposit and the booking of your listing "{listing}" is confirmed.
            {dates}
            Guests: {guests}"""),

    BOOKING_REJECTED(
            "Domaćin nije prihvatio rezervaciju: {listing}",
            """
            nažalost, domaćin nije prihvatio vašu rezervaciju oglasa „{listing}“.
            {dates}
            Razlog: {reason}""",
            "The host declined your booking: {listing}",
            """
            unfortunately, the host has declined your booking of "{listing}".
            {dates}
            Reason: {reason}"""),

    BOOKING_EXPIRED(
            "Rezervacija je istekla: {listing}",
            """
            vaša rezervacija oglasa „{listing}“ je istekla, jer nije potvrđena na vreme.
            {dates}""",
            "Your booking has expired: {listing}",
            """
            your booking of "{listing}" has expired because it was not confirmed in time.
            {dates}"""),

    BOOKING_CANCELLED_BY_GUEST(
            "Gost je otkazao rezervaciju: {listing}",
            """
            {guest} je otkazao rezervaciju vašeg oglasa „{listing}“.
            {dates}
            Razlog: {reason}""",
            "The guest cancelled the booking: {listing}",
            """
            {guest} has cancelled the booking of your listing "{listing}".
            {dates}
            Reason: {reason}"""),

    BOOKING_CANCELLED_BY_OWNER(
            "Domaćin je otkazao rezervaciju: {listing}",
            """
            nažalost, domaćin je otkazao vašu rezervaciju oglasa „{listing}“.
            {dates}
            Razlog: {reason}
            Vraćamo vam ceo uplaćen iznos: {refund}""",
            "The host cancelled your booking: {listing}",
            """
            unfortunately, the host has cancelled your booking of "{listing}".
            {dates}
            Reason: {reason}
            You get back everything you paid: {refund}"""),

    BOOKING_CANCELLED_MIN_NOT_REACHED(
            "Tura je otkazana, nije bilo dovoljno prijava: {listing}",
            """
            tura „{listing}“ je otkazana, jer se do roka nije prijavio najmanji broj gostiju.
            {dates}
            Vraćamo vam ceo uplaćen iznos: {refund}""",
            "The tour is cancelled, not enough guests: {listing}",
            """
            the tour "{listing}" is cancelled because not enough guests signed up by the deadline.
            {dates}
            You get back everything you paid: {refund}"""),

    TOUR_DATE_CONFIRMED(
            "Tura se održava: {listing}",
            """
            prijavio se najmanji broj gostiju i tura „{listing}“ se održava.
            {dates}""",
            "The tour takes place: {listing}",
            """
            enough guests have signed up and the tour "{listing}" takes place.
            {dates}"""),

    LISTING_APPROVED(
            "Oglas je odobren: {listing}",
            """
            vaš oglas „{listing}“ je odobren i vidljiv je gostima u pretrazi.""",
            "Your listing is approved: {listing}",
            """
            your listing "{listing}" is approved and guests can now find it."""),

    LISTING_REJECTED(
            "Oglas nije odobren: {listing}",
            """
            vaš oglas „{listing}“ nije odobren.
            Razlog: {reason}

            Oglas možete da ispravite kroz zahtev za izmenu i on će biti ponovo pregledan.""",
            "Your listing was not approved: {listing}",
            """
            your listing "{listing}" was not approved.
            Reason: {reason}

            You can correct it with a change request and it will be reviewed again."""),

    LISTING_REMOVED(
            "Oglas je uklonjen: {listing}",
            """
            administrator je uklonio vaš oglas „{listing}“. Oglas više nije u pretrazi i ne prima rezervacije, a postojeće rezervacije važe.
            Razlog: {reason}""",
            "Your listing was removed: {listing}",
            """
            an administrator has removed your listing "{listing}". It is no longer in search and takes no bookings; existing bookings stay valid.
            Reason: {reason}""");

    private static final Pattern PARAM = Pattern.compile("\\{(\\w+)}");

    private final String srSubject;
    private final String srBody;
    private final String enSubject;
    private final String enBody;

    MailTemplate(String srSubject, String srBody, String enSubject, String enBody) {
        this.srSubject = srSubject;
        this.srBody = srBody;
        this.enSubject = enSubject;
        this.enBody = enBody;
    }

    // a PENDING email, not saved yet
    EmailMessage render(User to, Map<String, String> params) {
        boolean sr = to.getLanguage() != Language.EN;
        String greeting = (sr ? "Zdravo " : "Hello ") + to.getFirstName() + ",\n\n";
        String body = (sr ? srBody : enBody).lines()
                .filter(line -> PARAM.matcher(line).results().allMatch(m -> present(params.get(m.group(1)))))
                .map(line -> fill(line, params))
                .collect(Collectors.joining("\n"));
        EmailMessage email = new EmailMessage();
        email.setRecipient(to);
        email.setSubject(fill(sr ? srSubject : enSubject, params));
        email.setBody(greeting + body + "\n\nVillage Booker");
        email.setStatus(EmailStatus.PENDING);
        return email;
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private static String fill(String line, Map<String, String> params) {
        Matcher m = PARAM.matcher(line);
        return m.replaceAll(r -> Matcher.quoteReplacement(params.getOrDefault(r.group(1), "")));
    }
}
