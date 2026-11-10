package com.villagebooker.service.notification;

import com.villagebooker.common.time.AppTime;
import com.villagebooker.enums.booking.BookingStatus;
import com.villagebooker.enums.listing.ListingStatus;
import com.villagebooker.enums.user.Language;
import com.villagebooker.model.Booking;
import com.villagebooker.model.EmailMessage;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Payment;
import com.villagebooker.model.User;
import com.villagebooker.repo.booking.BookingRepo;
import com.villagebooker.repo.listing.ListingRepo;
import com.villagebooker.repo.payment.PaymentRepo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// Which emails an event sends and to whom (spec 14), from the state at the end of the publishing transaction.
@Component
@RequiredArgsConstructor
public class NotificationMails {

    // APPROVED after an admin review; from PAUSED_BY_OWNER it is the owner's own resume
    private static final Set<ListingStatus> REVIEWED_FROM = Set.of(ListingStatus.PENDING, ListingStatus.REJECTED);

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy.");
    private static final DateTimeFormatter EN_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final BookingRepo bookingRepo;
    private final ListingRepo listingRepo;
    private final PaymentRepo paymentRepo;

    public List<EmailMessage> bookingCreated(BookingCreatedEvent event) {
        return bookingRepo.findWithPartiesById(event.bookingId())
                .map(b -> List.of(
                        MailTemplate.BOOKING_REQUESTED.render(b.getGuest(), params(b, b.getGuest())),
                        MailTemplate.BOOKING_AWAITS_OWNER.render(owner(b), params(b, owner(b)))))
                .orElse(List.of());
    }

    public List<EmailMessage> bookingStatusChanged(BookingStatusChangedEvent event) {
        Booking b = bookingRepo.findWithPartiesById(event.bookingId()).orElse(null);
        if (b == null) {
            return List.of();
        }
        User guest = b.getGuest();
        return switch (event.to()) {
            case AWAITING_PAYMENT -> List.of(MailTemplate.BOOKING_AWAITS_PAYMENT.render(guest, params(b, guest)));
            case CONFIRMED -> event.from() == BookingStatus.AWAITING_PAYMENT
                    ? List.of(MailTemplate.BOOKING_CONFIRMED.render(guest, params(b, guest)),
                            MailTemplate.BOOKING_PAID.render(owner(b), params(b, owner(b))))
                    : List.of(MailTemplate.BOOKING_CONFIRMED.render(guest, params(b, guest)));
            case REJECTED -> List.of(MailTemplate.BOOKING_REJECTED.render(guest, params(b, guest)));
            case EXPIRED -> List.of(MailTemplate.BOOKING_EXPIRED.render(guest, params(b, guest)));
            case CANCELLED_BY_GUEST ->
                    List.of(MailTemplate.BOOKING_CANCELLED_BY_GUEST.render(owner(b), params(b, owner(b))));
            case CANCELLED_BY_OWNER ->
                    List.of(MailTemplate.BOOKING_CANCELLED_BY_OWNER.render(guest, params(b, guest)));
            case CANCELLED_MIN_NOT_REACHED ->
                    List.of(MailTemplate.BOOKING_CANCELLED_MIN_NOT_REACHED.render(guest, params(b, guest)));
            case AWAITING_OWNER_APPROVAL, COMPLETED -> List.of();
        };
    }

    // every guest with an active booking on the date
    public List<EmailMessage> tourDateConfirmed(TourDateConfirmedEvent event) {
        return bookingRepo.findByTourDateIdAndStatusIn(event.tourDateId(), BookingStatus.ACTIVE).stream()
                .map(b -> MailTemplate.TOUR_DATE_CONFIRMED.render(b.getGuest(), params(b, b.getGuest())))
                .toList();
    }

    // only the admin's decisions; the owner's own pause, resume and delete send nothing
    public List<EmailMessage> listingStatusChanged(ListingStatusChangedEvent event) {
        MailTemplate template = switch (event.to()) {
            case APPROVED -> REVIEWED_FROM.contains(event.from()) ? MailTemplate.LISTING_APPROVED : null;
            case REJECTED -> MailTemplate.LISTING_REJECTED;
            case REMOVED_BY_ADMIN -> MailTemplate.LISTING_REMOVED;
            default -> null;
        };
        if (template == null) {
            return List.of();
        }
        return listingRepo.findById(event.listingId())
                .map(listing -> {
                    Map<String, String> params = new HashMap<>();
                    params.put("listing", listing.getName());
                    params.put("reason", event.to() == ListingStatus.REJECTED
                            ? listing.getRejectionReason() : listing.getRemovalReason());
                    return List.of(template.render(listing.getOwner(), params));
                })
                .orElse(List.of());
    }

    // not from an event: PasswordResetService saves it in its own transaction
    public EmailMessage passwordReset(User user, String link, Instant expiresAt) {
        return MailTemplate.PASSWORD_RESET.render(user,
                Map.of("link", link, "deadline", moment(expiresAt, user.getLanguage() != Language.EN)));
    }

    private static User owner(Booking booking) {
        return booking.getListing().getOwner();
    }

    // everything a booking email may show, in the recipient's language
    private Map<String, String> params(Booking b, User to) {
        boolean sr = to.getLanguage() != Language.EN;
        Listing listing = b.getListing();
        User guest = b.getGuest();
        Map<String, String> params = new HashMap<>();
        params.put("listing", listing.getName());
        params.put("dates", dates(b, sr));
        params.put("guests", String.valueOf(b.getGuestCount()));
        params.put("total", money(b.getTotalPrice()));
        params.put("due", b.getAmountDue().signum() > 0 ? money(b.getAmountDue()) : null);
        params.put("deposit", b.getDepositAmount() == null ? null : money(b.getDepositAmount()));
        params.put("guest", guest.getFirstName() + " " + guest.getLastName());
        params.put("note", b.getNote());
        params.put("reason", b.getCancellationReason());
        params.put("deadline", b.getExpiresAt() == null ? null : moment(b.getExpiresAt(), sr));
        if (BookingStatus.FINISHED.contains(b.getStatus())) {
            BigDecimal refund = paymentRepo.findByBookingIdOrderById(b.getId()).stream()
                    .map(Payment::getRefundAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            params.put("refund", refund.signum() > 0 ? money(refund) : null);
        }
        return params;
    }

    private static String dates(Booking b, boolean sr) {
        if (b.getTourDate() == null) {
            return (sr ? "Dolazak: " : "Arrival: ") + date(b.getStartDate(), sr) + "\n"
                    + (sr ? "Odlazak: " : "Departure: ") + date(b.getEndDate().plusDays(1), sr);
        }
        String days = b.getStartDate().equals(b.getEndDate())
                ? date(b.getStartDate(), sr)
                : date(b.getStartDate(), sr) + " - " + date(b.getEndDate(), sr);
        return (sr ? "Datum ture: " : "Tour date: ") + days;
    }

    private static String date(LocalDate date, boolean sr) {
        return (sr ? DATE : EN_DATE).format(date);
    }

    private static String moment(Instant instant, boolean sr) {
        var local = instant.atZone(AppTime.ZONE);
        return date(local.toLocalDate(), sr) + (sr ? " u " : " at ") + TIME.format(local);
    }

    private static String money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString() + " RSD";
    }
}
