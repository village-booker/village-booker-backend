# TODO: Specifikacija rezervacija, placanja i stanja (v1)

Ovaj fajl je referenca za implementaciju korak-po-korak. Fokus je produkcioni kvalitet, jasne state tranzicije, sigurnost i konkurentnost.

## 1) Zakljucane poslovne odluke

- Kapara je opciona i definise se na `Usluga`.
- `Usluga` dobija polje: `zahtevaPotvrduVlasnika` (`boolean`).
- Booking mode:
- `true` -> manual approval
- `false` -> instant booking
- Pravilo za kaparu (u `RezervacijaService`, ne u kontroleru):

```text
IF usluga.nacinPlacanja IN (ONLINE, KOMBINOVANO)
   AND usluga.kaparaProcenat != null
   AND usluga.kaparaProcenat > 0
THEN flow sa kaparom
ELSE flow bez kapare
```

- Ako je `kaparaProcenat = 0`, tretira se kao bez kapare.
- `preostaloZaPlatiti` se popunjava pri kreiranju rezervacije uvek.

## 2) Status model

### 2.1 StatusRezervacije (ukida se `NA_CEKANJU`)

- `CEKA_POTVRDU_VLASNIKA`
- `CEKA_PLACANJE`
- `ODOBRENO`
- `ODBIJENO`
- `ISTEKLO`
- `OTKAZANO_KORISNIK`
- `OTKAZANO_VLASNIK`
- `ZAVRSENO`

### 2.2 StatusPlacanja

- `NA_CEKANJU`
- `USPESNO`
- `NEUSPESNO`
- `REFUNDIRANO`

Refund ostaje jedan status (`REFUNDIRANO`), a razlika parcijalni/puni refund ide preko iznosa.

## 3) Flow (state machine)

### Manual + bez kapare

- `CEKA_POTVRDU_VLASNIKA -> ODOBRENO -> ZAVRSENO`
- `CEKA_POTVRDU_VLASNIKA -> ODBIJENO`
- `CEKA_POTVRDU_VLASNIKA -> ISTEKLO`

### Manual + kapara

- `CEKA_POTVRDU_VLASNIKA -> CEKA_PLACANJE -> ODOBRENO -> ZAVRSENO`
- `CEKA_POTVRDU_VLASNIKA -> ISTEKLO`
- `CEKA_PLACANJE -> ISTEKLO`

### Instant + kapara

- `CEKA_PLACANJE -> ODOBRENO -> ZAVRSENO`
- `CEKA_PLACANJE -> ISTEKLO`

### Instant + bez kapare

- `ODOBRENO -> ZAVRSENO`

### Otkazivanje (iz aktivnih stanja)

- `-> OTKAZANO_KORISNIK`
- `-> OTKAZANO_VLASNIK`

## 4) Placanje i refund model

`Placanje` obavezno vezati na `Rezervacija` (`ManyToOne`).

Polja:

- `tip` (`KAPARA`, `OSTATAK`, `PUNO`)
- `status` (`StatusPlacanja`)
- `iznos` (`BigDecimal`)
- `refundIznos` (`BigDecimal`)
- `paymentIntentId` (Stripe ID)
- `stripeRefundId` (poslednji refund ID ili drugi dogovoreni audit model)

Pravila:

- `refundIznos = 0` -> nema refunda
- `refundIznos < iznos` -> parcijalni refund
- `refundIznos == iznos` -> puni refund

## 5) Concurrency i overlap zastita

- Datumski interval je poluotvoren: `[rezervacija_od, rezervacija_do)`.
- DB `CHECK`: `rezervacija_od < rezervacija_do`.
- PostgreSQL `EXCLUDE` constraint po `usluga_id` + overlap, samo za aktivne statuse:

```
WHERE status IN ('CEKA_POTVRDU_VLASNIKA', 'CEKA_PLACANJE', 'ODOBRENO')
```

- Aplikaciono: insert u transakciji, conflict iz DB -> HTTP `409`.
- Dodatno: najvise jedno aktivno pending placanje po rezervaciji (partial unique index).

## 6) Transakcione granice

- Transakcije na service layer-u, ne u kontroleru.
- Komande (`create`, `approve`, `cancel`, `refund`) -> `@Transactional`.
- Read operacije -> `@Transactional(readOnly = true)`.
- Spoljne Stripe pozive ne drzati u dugoj DB transakciji.
- Webhook obrada: kratka transakcija (idempotency event + update `Placanje` + update `Rezervacija`).

## 7) Stripe integracija i security

- Izvor istine za konacan status je webhook (ne redirect).
- Mapiranje:
- `payment_intent.succeeded` -> `USPESNO`
- `payment_intent.payment_failed` -> `NEUSPESNO`
- `charge.refunded` / `refund.updated` -> `REFUNDIRANO`
- Obavezna verifikacija potpisa (`Stripe-Signature` + `Webhook.constructEvent(...)`).
- Idempotency tabela `stripe_webhook_event` sa `UNIQUE(event_id)`.

## 8) Timeout mehanizam

- `CEKA_POTVRDU_VLASNIKA -> ISTEKLO` nakon `12h`.
- `CEKA_PLACANJE -> ISTEKLO` nakon `15m`.
- Scheduler (`@Scheduled`) menja samo status.
- Rezervisani termini se automatski oslobadjaju jer overlap blokira samo aktivne statuse.
- Konfigurabilno kroz `application.yml` (ne hardcode).
- ShedLock: preporuka da bude ukljucen odmah zbog multi-instance sigurnosti.

## 9) Soft delete strategija

Soft delete:

- `Usluga`
- `Smestaj`
- `Tura`
- `Karakteristika`
- `UslugaSlika`
- `OmiljenaUsluga`

Bez soft delete:

- `Rezervacija`
- `Placanje`
- `Recenzija`
- `UslugaIstorija`

Implementacija:

- `deleted_at`
- opciono `deleted_by` (`Long` FK ka `Korisnik`)
- `@SQLDelete`
- Hibernate `@Filter`
- unique indeksi kao partial: `WHERE deleted_at IS NULL`

Napomena: kod `JOINED` nasledjivanja soft delete se vodi na baznoj `Usluga`.

## 10) DTO sloj i API granice

- Entitete ne izlagati direktno.
- Koristiti `Request DTO` + validaciju.
- Koristiti `Response DTO`.
- Mapiranje preko MapStruct.
- Za liste koristiti projekcije/light DTO gde je moguce.

## 11) Optimistic locking

Dodati `@Version` na:

- `Rezervacija`
- `Usluga`
- `Placanje`

Minimum obavezno: `Rezervacija` (konkurentni update statusa).

## 12) Enum ciljna lista (za uskladjivanje)

- `PolitikaOtkazivanja`: `BESPLATNO`, `PARCIJALNO`, `BEZ_POVRATA`
- `StatusOmiljene`: `AKTIVNA`, `NEAKTIVNA`
- `StatusRezervacije`: `CEKA_POTVRDU_VLASNIKA`, `CEKA_PLACANJE`, `ODOBRENO`, `ODBIJENO`, `ISTEKLO`, `OTKAZANO_KORISNIK`, `OTKAZANO_VLASNIK`, `ZAVRSENO`
- `StatusUsluge`: `NA_CEKANJU`, `ODOBRENA`, `ODBIJENO`, `PAUZIRANA_VLASNIK`, `OBRISANA`
- `StatusZahteva`: `NA_CEKANJU`, `ODOBREN`, `ODBIJEN`
- `Uloga`: `KORISNIK`, `VLASNIK`, `ADMIN`
- `TezinaTure`: `LAKO`, `SREDNJE`, `TESKO`, `EKSTREMNO`
- `TipPlacanja`: `KAPARA`, `OSTATAK`, `PUNO`
- `StatusPlacanja`: `NA_CEKANJU`, `USPESNO`, `NEUSPESNO`, `REFUNDIRANO`
- `NacinPlacanja`: `NA_LICU_MESTA`, `ONLINE`, `KOMBINOVANO`
- `TipUsluge`: `SMESTAJ`, `TURA`

## 13) Predlog redosleda implementacije

1. Enum uskladjivanje i vidljivost (`public`), bez menjanja business logike.
2. Money refactor (`BigDecimal`) + migracije kolona.
3. Dodavanje novih entiteta: `Placanje`, `Recenzija`, `ZahtevZaIzmenuUsluge`, `NedostupanPeriod`.
4. Relacije i ogranicenja (FK, unique, check, exclude).
5. `@Version` i transakcione granice u servisima.
6. State machine pravila u `RezervacijaService`.
7. Stripe webhook + idempotency + signature verifikacija.
8. Scheduler timeout logika + konfiguracija.
9. Soft delete i filteri.
10. DTO + MapStruct sloj.
11. Testovi:
- unit testovi state machine-a
- integracioni testovi overlap/concurrency
- webhook idempotency testovi
- scheduler expiry testovi
