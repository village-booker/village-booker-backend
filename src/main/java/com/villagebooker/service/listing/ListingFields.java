package com.villagebooker.service.listing;

import com.villagebooker.enums.listing.CancellationPolicy;
import com.villagebooker.enums.payment.PaymentMethod;
import com.villagebooker.enums.listing.TourDifficulty;
import com.villagebooker.model.Accommodation;
import com.villagebooker.model.Listing;
import com.villagebooker.model.Tour;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

// The fields of a listing an owner may change, as text: used to compute and apply change requests.
// Text keeps money exact through jsonb and makes 5000 and 5000.00 the same value.
// A new editable field on Listing / Accommodation / Tour must be added here, otherwise it cannot be changed
// through a change request. rating, reviewCount, status, owner, version are never editable.
final class ListingFields {

    private record Field(String name,
                         Class<? extends Listing> type,
                         Function<Listing, Object> getter,
                         BiConsumer<Listing, String> setter) {
    }

    private static final List<Field> FIELDS = List.of(
            common("name", Listing::getName, Listing::setName),
            common("description", Listing::getDescription, Listing::setDescription),
            common("requiresOwnerApproval", Listing::isRequiresOwnerApproval,
                    (l, v) -> l.setRequiresOwnerApproval(Boolean.parseBoolean(v))),
            common("minGuests", Listing::getMinGuests, (l, v) -> l.setMinGuests(toInt(v))),
            common("maxGuests", Listing::getMaxGuests, (l, v) -> l.setMaxGuests(toInt(v))),
            common("address", Listing::getAddress, Listing::setAddress),
            common("place", Listing::getPlace, Listing::setPlace),
            common("municipality", Listing::getMunicipality, Listing::setMunicipality),
            common("district", Listing::getDistrict, Listing::setDistrict),
            common("latitude", Listing::getLatitude, (l, v) -> l.setLatitude(toDecimal(v))),
            common("longitude", Listing::getLongitude, (l, v) -> l.setLongitude(toDecimal(v))),
            common("cancellationPolicy", Listing::getCancellationPolicy,
                    (l, v) -> l.setCancellationPolicy(v == null ? null : CancellationPolicy.valueOf(v))),
            common("paymentMethod", Listing::getPaymentMethod,
                    (l, v) -> l.setPaymentMethod(v == null ? null : PaymentMethod.valueOf(v))),
            common("depositPercentage", Listing::getDepositPercentage, (l, v) -> l.setDepositPercentage(toDecimal(v))),
            common("freeCancellationDays", Listing::getFreeCancellationDays, (l, v) -> l.setFreeCancellationDays(toInt(v))),
            common("refundPercentage", Listing::getRefundPercentage, (l, v) -> l.setRefundPercentage(toDecimal(v))),

            accommodation("basePricePerNight", Accommodation::getBasePricePerNight,
                    (a, v) -> a.setBasePricePerNight(toDecimal(v))),
            accommodation("baseGuests", Accommodation::getBaseGuests, (a, v) -> a.setBaseGuests(toInt(v))),
            accommodation("extraGuestPrice", Accommodation::getExtraGuestPrice,
                    (a, v) -> a.setExtraGuestPrice(toDecimal(v))),
            accommodation("doubleBedCount", Accommodation::getDoubleBedCount, (a, v) -> a.setDoubleBedCount(toInt(v))),
            accommodation("singleBedCount", Accommodation::getSingleBedCount, (a, v) -> a.setSingleBedCount(toInt(v))),
            accommodation("roomCount", Accommodation::getRoomCount, (a, v) -> a.setRoomCount(toInt(v))),

            tour("durationDays", Tour::getDurationDays, (t, v) -> t.setDurationDays(toInt(v))),
            tour("difficulty", Tour::getDifficulty, (t, v) -> t.setDifficulty(v == null ? null : TourDifficulty.valueOf(v))),
            tour("pricePerPerson", Tour::getPricePerPerson, (t, v) -> t.setPricePerPerson(toDecimal(v)))
    );

    private ListingFields() {
    }

    static Map<String, Object> snapshot(Listing listing) {
        listing = real(listing);
        Map<String, Object> values = new LinkedHashMap<>();
        for (Field field : FIELDS) {
            if (field.type().isInstance(listing)) {
                values.put(field.name(), toText(field.getter().apply(listing)));
            }
        }
        return values;
    }

    static Map<String, Object> changes(Listing before, Listing after) {
        Map<String, Object> old = snapshot(before);
        Map<String, Object> changes = new LinkedHashMap<>();
        snapshot(after).forEach((name, value) -> {
            if (!Objects.equals(old.get(name), value)) {
                changes.put(name, value);
            }
        });
        return changes;
    }

    static Map<String, Object> valuesOf(Listing listing, Iterable<String> names) {
        Map<String, Object> all = snapshot(listing);
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : names) {
            values.put(name, all.get(name));
        }
        return values;
    }

    static void apply(Listing lazyOrReal, Map<String, Object> changes) {
        Listing listing = real(lazyOrReal);
        changes.forEach((name, value) -> {
            Field field = FIELDS.stream()
                    .filter(f -> f.name().equals(name) && f.type().isInstance(listing))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "Field " + name + " cannot be applied to " + listing.getClass().getSimpleName()));
            field.setter().accept(listing, value == null ? null : value.toString());
        });
    }

    // a lazy Listing proxy is not an Accommodation / Tour, the real object behind it is
    private static Listing real(Listing listing) {
        return (Listing) Hibernate.unproxy(listing);
    }

    private static String toText(Object value) {
        return switch (value) {
            case null -> null;
            case BigDecimal decimal -> decimal.stripTrailingZeros().toPlainString();
            case Enum<?> constant -> constant.name();
            default -> value.toString();
        };
    }

    private static Integer toInt(String value) {
        return value == null ? null : Integer.valueOf(value);
    }

    private static BigDecimal toDecimal(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static Field common(String name, Function<Listing, Object> getter, BiConsumer<Listing, String> setter) {
        return new Field(name, Listing.class, getter, setter);
    }

    @SuppressWarnings("unchecked")
    private static <T extends Listing> Field typed(String name, Class<T> type, Function<T, Object> getter,
                                                   BiConsumer<T, String> setter) {
        return new Field(name, type, l -> getter.apply((T) l), (l, v) -> setter.accept((T) l, v));
    }

    private static Field accommodation(String name, Function<Accommodation, Object> getter,
                                       BiConsumer<Accommodation, String> setter) {
        return typed(name, Accommodation.class, getter, setter);
    }

    private static Field tour(String name, Function<Tour, Object> getter, BiConsumer<Tour, String> setter) {
        return typed(name, Tour.class, getter, setter);
    }
}
