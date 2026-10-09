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

### 1.1 Snapshot uslova na rezervaciji

Gost ima pravo na uslove pod kojima je rezervisao. Vlasnik moze kasnije da menja `Usluga`, ali to ne sme da utice na postojece rezervacije.

Pri kreiranju rezervacije servis kopira sa `Usluga` u `Rezervacija`:

| `Usluga` | `Rezervacija` | Napomena |
|---|---|---|
| cena (`baznaCenaNoci`... / `cenaPoOsobi`) | `ukupnaCena`, `preostaloZaPlatiti` | izracunato |
| `politikaOtkazivanja` | `politikaOtkazivanja` | `NOT NULL` |
| `rokBesplatnogOtkazivanja` (broj dana) | `besplatnoOtkazivanjeDo` (`LocalDateTime`) | izracunato, vidi ispod |
| `procenatPovrata` | `procenatPovrata` | nullable |
| `kaparaProcenat` | `kaparaProcenat` | nullable |
| `nacinPlacanja` | `nacinPlacanja` | `NOT NULL` |

Pravila:

- Vrednosti se popunjavaju **samo u servisu** pri kreiranju, nikad iz request DTO-a, i posle se ne menjaju.
- Sva logika otkazivanja i refunda cita **samo polja sa rezervacije**, nikad sa `Usluga`.
- `Usluga.rokBesplatnogOtkazivanja` = broj dana pre `rezervacijaOd` do kog je otkazivanje besplatno.
- `besplatnoOtkazivanjeDo = rezervacijaOd.atStartOfDay().minusDays(rokBesplatnogOtkazivanja)`.
- Kod `BEZ_POVRATA` (ili kad `rokBesplatnogOtkazivanja` nije postavljen): `besplatnoOtkazivanjeDo = null`.
- Ako je gost rezervisao kad je rok vec prosao (npr. rok 7 dana, rezervacija 3 dana pre dolaska), `besplatnoOtkazivanjeDo` je u proslosti -> nema besplatnog otkazivanja. (Opciono kasnije: grace period npr. 24h od `vremeRezervisanja`.)

## 2) Status model

### 2.1 StatusRezervacije (ukida se `NA_CEKANJU`)

- `CEKA_POTVRDU_VLASNIKA`
- `CEKA_PLACANJE`
- `ODOBRENO`
- `ODBIJENO`
- `ISTEKLO`
- `OTKAZANO_KORISNIK`
- `OTKAZANO_VLASNIK`
- `OTKAZANO_NEDOVOLJNO_PRIJAVA` (samo tura: termin nije skupio minimum do `rokZaMin`)
- `ZAVRSENO`

Za turu nema posebnog statusa "ceka minimum". Rezervacija je `ODOBRENO`, a da li ce se tura odrzati cita se iz `TerminTure.status` (`OTVOREN` = jos se ceka minimum, `POTVRDJEN`/`POPUNJEN` = sigurno).

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
- `-> OTKAZANO_NEDOVOLJNO_PRIJAVA` (scheduler, samo tura)

### Iznos refunda pri otkazivanju

Osnovica = zbir `iznos` svih `Placanje` sa statusom `USPESNO` za tu rezervaciju. Koriste se samo snapshot polja sa rezervacije (1.1).

| Ko / zasto otkazuje | Refund |
|---|---|
| `OTKAZANO_VLASNIK` | 100%, bez obzira na politiku |
| `OTKAZANO_NEDOVOLJNO_PRIJAVA` | 100%, bez obzira na politiku |
| `OTKAZANO_KORISNIK`, `now < besplatnoOtkazivanjeDo` | 100% (`BESPLATNO` i `PARCIJALNO`) |
| `OTKAZANO_KORISNIK`, posle roka, `PARCIJALNO` | `procenatPovrata`% osnovice |
| `OTKAZANO_KORISNIK`, posle roka, `BESPLATNO` | 0% |
| `OTKAZANO_KORISNIK`, `BEZ_POVRATA` | 0% |

- Zaokruzivanje: `setScale(2, RoundingMode.HALF_UP)`.
- Otkazivanje pre bilo kakve uplate (npr. `CEKA_POTVRDU_VLASNIKA`, `NA_LICU_MESTA`) -> nema refunda, samo promena statusa.

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
- **Overlap vazi samo za smestaj.** `EXCLUDE` mora imati i `AND termin_id IS NULL`, inace bi blokirao vise grupa na istom terminu ture.

### 5.1 Ture i termini (vise grupa na jednoj turi)

- Gost se prijavljuje na `TerminTure`, ne bira datume. Request sadrzi `terminId` + `brOsoba`.
- Servis popunjava: `termin`, `usluga = termin.tura`, `rezervacijaOd/Do` iz termina, `brojDana = tura.brojDana`, `ukupnaCena = cenaPoOsobi * brOsoba`.
- Invarijanta: `termin != null` => `termin.tura.id == usluga.id`.
- `minBrOsoba` / `maksBrOsoba` kod ture vaze za **ceo termin**, ne za jednu grupu.
- Kapacitet: u transakciji, uz `PESSIMISTIC_WRITE` lock na termin (ili `@Version` + retry):
  - `brPrijavljenih + brOsoba > tura.maksBrOsoba` -> HTTP `409`
  - inace `brPrijavljenih += brOsoba`
  - `brPrijavljenih >= minBrOsoba` -> termin `POTVRDJEN`
  - `brPrijavljenih == maksBrOsoba` -> termin `POPUNJEN`
- Otkazivanje / istek rezervacije: `brPrijavljenih -= brOsoba`, `POPUNJEN` -> `POTVRDJEN` (ili `OTVOREN` ako padne ispod minimuma pre `rokZaMin`).
- Kapacitet zauzimaju aktivni statusi: `CEKA_POTVRDU_VLASNIKA`, `CEKA_PLACANJE`, `ODOBRENO`.
- Termin sa prijavama se ne pomera; vlasnik ga otkazuje (`OTKAZAN` -> sve rezervacije `OTKAZANO_VLASNIK`, puni refund) i pravi novi.

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
- Smestaj: rezervisani termini se automatski oslobadjaju jer overlap blokira samo aktivne statuse.
- Tura: pri isteku rezervacije scheduler umanjuje `TerminTure.brPrijavljenih` (vidi 5.1).
- Tura: posle `TerminTure.rokZaMin`, ako je `brPrijavljenih < minBrOsoba` -> termin `OTKAZAN`, sve aktivne rezervacije `OTKAZANO_NEDOVOLJNO_PRIJAVA`, puni refund.
- Tura: termin posle `datumDo` -> `ZAVRSEN`.
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
- `TerminTure` (vec dodato; stiti `brPrijavljenih` od overbookinga)

Minimum obavezno: `Rezervacija` (konkurentni update statusa) i `TerminTure`.

## 12) Enum ciljna lista (za uskladjivanje)

- `PolitikaOtkazivanja`: `BESPLATNO`, `PARCIJALNO`, `BEZ_POVRATA`
- `StatusOmiljene`: `AKTIVNA`, `NEAKTIVNA`
- `StatusRezervacije`: `CEKA_POTVRDU_VLASNIKA`, `CEKA_PLACANJE`, `ODOBRENO`, `ODBIJENO`, `ISTEKLO`, `OTKAZANO_KORISNIK`, `OTKAZANO_VLASNIK`, `OTKAZANO_NEDOVOLJNO_PRIJAVA`, `ZAVRSENO`
- `StatusTermina`: `OTVOREN`, `POTVRDJEN`, `POPUNJEN`, `OTKAZAN`, `ZAVRSEN`
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
