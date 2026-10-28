package com.villagebooker.enums.listing;

// PRICE_* only together with a listing type: a price per night and a price per person are not comparable
public enum ListingSort {
    NEWEST,
    RATING,
    PRICE_ASC,
    PRICE_DESC
}
