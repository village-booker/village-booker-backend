# TODO: Specifikacija rezervacija, placanja i stanja (v1)

Ovaj fajl je referenca za implementaciju korak-po-korak. Fokus je produkcioni kvalitet, jasne state tranzicije, sigurnost i konkurentnost.

## 0) Recnik: kod je na engleskom

Tekst ispod koristi srpske nazive; u kodu i bazi vaze engleski. Tabele su snake_case u jednini.

| Spec (srpski) | Klasa | Tabela |
|---|---|---|
| `Korisnik` | `User` | `app_user` (`user` je rezervisana rec u Postgresu) |
| `Usluga` | `Listing` | `listing` |
| `Smestaj` | `Accommodation` | `accommodation` |
| `Tura` | `Tour` | `tour` |
| `TerminTure` | `TourDate` | `tour_date` |
| `Rezervacija` | `Booking` | `booking` |
| `Placanje` | `Payment` | `payment` |
| `Recenzija` | `Review` | `review` |
| `NedostupanPeriod` | `UnavailablePeriod` | `unavailable_period` |
| `OmiljenaUsluga` | `Favorite` | `favorite` |
| `UslugaSlika` | `ListingImage` | `listing_image` |
| `UslugaIstorijaStatusa` | `ListingStatusHistory` | `listing_status_history` |
| `ZahtevZaIzmenuUsluge` | `ListingChangeRequest` | `listing_change_request` |
| `Karakteristika` | `Feature` | `feature` (veza: `listing_feature`) |

Vazna polja:

| Spec | Kod |
|---|---|
| `vlasnik` / `gost` | `owner` / `guest` |
| `rezervacijaOd` / `rezervacijaDo`, `datumOd` / `datumDo` | `startDate` / `endDate` |
| `brOsoba`, `minBrOsoba`, `maksBrOsoba` | `guestCount`, `minGuests`, `maxGuests` |
| `ukupnaCena`, `preostaloZaPlatiti` | `totalPrice`, `amountDue` |
| `isticeU` | `expiresAt` |
| `politikaOtkazivanja`, `rokBesplatnogOtkazivanja`, `besplatnoOtkazivanjeDo` | `cancellationPolicy`, `freeCancellationDays`, `freeCancellationUntil` |
| `procenatPovrata`, `kaparaProcenat` | `refundPercentage`, `depositPercentage` |
| `nacinPlacanja` (Usluga/Rezervacija), `nacin` (Placanje) | `paymentMethod`, `channel` |
| `otkazanoAt`, `razlogOtkazivanja` | `cancelledAt`, `cancellationReason` |
| `termin`, `brPrijavljenih`, `rokZaMin` | `tourDate`, `bookedCount`, `minGuestsDeadline` |
| `zahtevaPotvrduVlasnika` | `requiresOwnerApproval` |
| `getBrojNoci()` | `getDayCount()` |

Enumi:

| Spec | Kod |
|---|---|
| `StatusRezervacije`: `CEKA_POTVRDU_VLASNIKA`, `CEKA_PLACANJE`, `ODOBRENO`, `ODBIJENO`, `ISTEKLO`, `OTKAZANO_KORISNIK`, `OTKAZANO_VLASNIK`, `OTKAZANO_NEDOVOLJNO_PRIJAVA`, `ZAVRSENO` | `BookingStatus`: `AWAITING_OWNER_APPROVAL`, `AWAITING_PAYMENT`, `CONFIRMED`, `REJECTED`, `EXPIRED`, `CANCELLED_BY_GUEST`, `CANCELLED_BY_OWNER`, `CANCELLED_MIN_NOT_REACHED`, `COMPLETED` |
| `StatusTermina`: `OTVOREN`, `POTVRDJEN`, `POPUNJEN`, `OTKAZAN`, `ZAVRSEN` | `TourDateStatus`: `OPEN`, `CONFIRMED`, `FULL`, `CANCELLED`, `COMPLETED` |
| `StatusUsluge`: `NA_CEKANJU`, `ODOBRENA`, `ODBIJENO`, `PAUZIRANA_VLASNIK`, `OBRISANA` | `ListingStatus`: `PENDING`, `APPROVED`, `REJECTED`, `PAUSED_BY_OWNER`, `DELETED` |
| `StatusPlacanja`: `NA_CEKANJU`, `USPESNO`, `NEUSPESNO`, `REFUNDIRANO` | `PaymentStatus`: `PENDING`, `SUCCEEDED`, `FAILED`, `REFUNDED` |
| `StatusZahteva`: `NA_CEKANJU`, `ODOBREN`, `ODBIJEN` | `ChangeRequestStatus`: `PENDING`, `APPROVED`, `REJECTED` |
| `PolitikaOtkazivanja`: `BESPLATNO`, `PARCIJALNO`, `BEZ_POVRATA` | `CancellationPolicy`: `FREE`, `PARTIAL`, `NON_REFUNDABLE` |
| `NacinPlacanja`: `NA_LICU_MESTA`, `ONLINE`, `KOMBINOVANO` | `PaymentMethod`: `ON_SITE`, `ONLINE`, `COMBINED` |
| `NacinUplate`: `ONLINE`, `NA_LICU_MESTA` | `PaymentChannel`: `ONLINE`, `ON_SITE` |
| `TipPlacanja`: `KAPARA`, `OSTATAK`, `PUNO` | `PaymentType`: `DEPOSIT`, `BALANCE`, `FULL` |
| `TezinaTure`: `LAKO`, `SREDNJE`, `TESKO`, `EKSTREMNO` | `TourDifficulty`: `EASY`, `MODERATE`, `HARD`, `EXTREME` |
| `TipUsluge`: `SMESTAJ`, `TURA` | `ListingType`: `ACCOMMODATION`, `TOUR` |
| `Uloga`: `KORISNIK`, `VLASNIK`, `ADMIN` | `Role`: `USER`, `OWNER`, `ADMIN` |

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
| `rokBesplatnogOtkazivanja` (broj dana) | `besplatnoOtkazivanjeDo` (`Instant`) | izracunato, vidi ispod |
| `procenatPovrata` | `procenatPovrata` | nullable |
| `kaparaProcenat` | `kaparaProcenat` | nullable |
| `nacinPlacanja` | `nacinPlacanja` | `NOT NULL` |

Pravila:

- Vrednosti se popunjavaju **samo u servisu** pri kreiranju, nikad iz request DTO-a, i posle se ne menjaju.
- Sva logika otkazivanja i refunda cita **samo polja sa rezervacije**, nikad sa `Usluga`.
- `Usluga.rokBesplatnogOtkazivanja` = broj dana pre `rezervacijaOd` do kog je otkazivanje besplatno.
- `besplatnoOtkazivanjeDo = rezervacijaOd.minusDays(rokBesplatnogOtkazivanja).atStartOfDay(ZoneId.of("Europe/Belgrade")).toInstant()`.
- Kod `BEZ_POVRATA` (ili kad `rokBesplatnogOtkazivanja` nije postavljen): `besplatnoOtkazivanjeDo = null`.
- Ako je gost rezervisao kad je rok vec prosao (npr. rok 7 dana, rezervacija 3 dana pre dolaska), `besplatnoOtkazivanjeDo` je u proslosti -> nema besplatnog otkazivanja. (Opciono kasnije: grace period npr. 24h od `vremeRezervisanja`.)

Sta se **ne** cuva na rezervaciji (izvodi se):

- vlasnik -> `rezervacija.usluga.vlasnik` (JPQL: `r.usluga.vlasnik.id = :id`)
- broj noci / dana -> `Rezervacija.getBrojNoci()` = `DAYS.between(rezervacijaOd, rezervacijaDo) + 1`

### 1.3 Broj gostiju i cena

**Smestaj** (jedna rezervacija zauzima ceo smestaj):

| Polje | Znacenje |
|---|---|
| `minBrOsoba` (`minGuests`) | najmanje gostiju u jednoj rezervaciji (obicno 1) |
| `maksBrOsoba` (`maxGuests`) | kapacitet, najvise gostiju u jednoj rezervaciji |
| `bazanBrOsoba` (`baseGuests`) | koliko gostiju je ukljuceno u `baznaCenaNoci` |

```
cena za noc = baznaCenaNoci + max(0, brOsoba - bazanBrOsoba) * dodatnaCenaOsoba
ukupnaCena  = cena za noc * broj noci
```

- Ispod `bazanBrOsoba` cena se ne spusta (vlasnik ima iste troskove). Ko zeli jeftinije za manje gostiju, postavi manji `bazanBrOsoba`.
- Servis proverava (nije moguce kao DB `CHECK`, polja su u dve tabele):
  - pri kreiranju / izmeni smestaja: `minBrOsoba <= bazanBrOsoba <= maksBrOsoba`
  - pri rezervaciji: `minBrOsoba <= brOsoba <= maksBrOsoba`

**Tura:** `minBrOsoba` / `maksBrOsoba` vaze za **ceo termin** (zbir svih grupa, vidi 5.1). `ukupnaCena = cenaPoOsobi * brOsoba`.

## 1.2 Konvencije modela

- **Vreme:** trenutak dogadjaja (`kreiranAt`, `vremeRezervisanja`, `isticeU`, `besplatnoOtkazivanjeDo`, `rokZaMin`, `vreme` placanja...) je `Instant` -> `timestamptz`. Datumi boravka/termina (`rezervacijaOd/Do`, `datumOd/Do`) su `LocalDate`. Konverzija lokalnog datuma u trenutak uvek sa `ZoneId.of("Europe/Belgrade")`.
- **Intervali datuma** su zatvoreni `[od, do]` svuda: `Rezervacija`, `NedostupanPeriod`, `TerminTure`. Svaki datum u intervalu je jedan zauzet dan. Svaka tabela ima `CHECK od <= do`.
  - Smestaj: datum = **noc koja pocinje tog dana**. `rezervacijaDo` je poslednja noc; dan odlaska = `rezervacijaDo + 1` (samo za prikaz, ne cuva se). Dolazak 12.10, odlazak 14.10 -> `[12.10, 13.10]`.
  - Tura: datum = dan ture. Tura 12.10-13.10 -> `[12.10, 13.10]`.
  - Preklapanje: `a.od <= b.do AND b.od <= a.do`. Gost koji odlazi 14.10 i gost koji dolazi 14.10 se ne preklapaju (`[12.10, 13.10]` i `[14.10, 15.10]`).
- **Novac:** `BigDecimal`, `numeric(19,2)`. **Procenti:** `numeric(5,2)` + `CHECK 0..100`. **Koordinate:** `numeric(9,6)`. `BigDecimal` bez `precision/scale` je zabranjen (Hibernate default je `numeric(38,2)`).
- **Imena:** camelCase u Javi, snake_case samo u `@Column(name = ...)`.
- **Kolekcije:** `@ManyToOne` uvek (to je FK). `@OneToMany` / inverzni `@OneToOne` samo kad roditelj upravlja zivotom dece (`cascade` + `orphanRemoval`): trenutno `Usluga.slike`, `Usluga.nedostupniPeriodi`, plus `Usluga.karakteristike` (`@ManyToMany`, vlasnik join tabele). Sve ostalo kroz upite u repository-ju, sa paginacijom.
- **Indeksi:** svaka FK kolona po kojoj se pretrazuje ima `@Index` (Postgres ih ne pravi automatski).
- **Ograničenja u bazi:** `CHECK` preko `@Table(check = @CheckConstraint(...))`. `ddl-auto=update` ih ne dodaje na postojece tabele i ne menja tipove kolona -> posle izmene modela u razvoju obrisati semu. Sto Hibernate ne ume (`EXCLUDE`, `UNIQUE (lower(email))`, partial indeksi) ide u prvu Flyway migraciju.

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

- `tipPlacanja` (`KAPARA`, `OSTATAK`, `PUNO`)
- `nacin` (`NacinUplate`: `ONLINE`, `NA_LICU_MESTA`) - jedna uplata nikad nije `KOMBINOVANO`; `KOMBINOVANO` postoji samo na `Usluga`/`Rezervacija` (`NacinPlacanja`)
- `status` (`StatusPlacanja`)
- `iznos` (`BigDecimal`, `CHECK iznos > 0`)
- `refundIznos` (`BigDecimal`) - **jos nije u modelu**, dodati uz refund logiku
- `stripePaymentId` (Stripe PaymentIntent ID)
- `stripeRefundId` (poslednji refund ID ili drugi dogovoreni audit model)

Pravila:

- `refundIznos = 0` -> nema refunda
- `refundIznos < iznos` -> parcijalni refund
- `refundIznos == iznos` -> puni refund

## 5) Concurrency i overlap zastita

- Datumski interval je zatvoren: `[rezervacija_od, rezervacija_do]` (vidi 1.2).
- DB `CHECK`: `rezervacija_od <= rezervacija_do`.
- PostgreSQL `EXCLUDE` constraint po `usluga_id` + overlap `daterange(rezervacija_od, rezervacija_do, '[]')`, samo za aktivne statuse:

```
WHERE status IN ('CEKA_POTVRDU_VLASNIKA', 'CEKA_PLACANJE', 'ODOBRENO')
```

- Aplikaciono: insert u transakciji, conflict iz DB -> HTTP `409`.
- Dodatno: najvise jedno aktivno pending placanje po rezervaciji (partial unique index).
- **Overlap vazi samo za smestaj.** `EXCLUDE` mora imati i `AND termin_id IS NULL`, inace bi blokirao vise grupa na istom terminu ture.
- `EXCLUDE` zahteva `CREATE EXTENSION IF NOT EXISTS btree_gist;` i ide u Flyway migraciju.

### 5.0 Rezervacija vs. NedostupanPeriod (smestaj)

Datum je zauzet ako se preklapa sa aktivnom rezervacijom **ili** sa `NedostupanPeriod`. `EXCLUDE` radi samo unutar jedne tabele, pa ne pokriva oba slucaja (race: gost rezervise dok vlasnik istovremeno dodaje nedostupan period).

- Oba toka zakljucavaju **isti red usluge** pre provere:
  - kreiranje rezervacije smestaja
  - dodavanje / izmena `NedostupanPeriod`
- `ListingRepo.findByIdForUpdate(id)` sa `@Lock(PESSIMISTIC_WRITE)` -> `SELECT ... FOR UPDATE`.
- Pod lockom: proveri preklapanje u **obe** tabele (`BookingRepo.existsOverlapping(..., BookingStatus.ACTIVE)`, `UnavailablePeriodRepo.existsOverlapping(...)`) -> insert -> commit. Konflikt -> HTTP `409`.
- Brisanje usluge (9.3) uzima isti lock.

**Pravila za lock (vaze i za `TourDateRepo.findByIdForUpdate`):**

- Samo unutar `@Transactional` metode servisa; lock se pusta na commit/rollback.
- Transakcija pod lockom je kratka: lock -> provera -> upis -> commit. **Nikad** unutar nje: slanje mejla, HTTP pozivi, Stripe (vidi 14).
- Svaki tok koji menja zauzetost usluge mora da uzme lock; jedan tok bez locka rusi zastitu.
- Kad se zakljucava vise redova, uvek isti redosled: prvo `Listing`, pa `TourDate` (inace deadlock).
- Proveriti jednom u logu (`show-sql=true`) da Hibernate za JOINED nasledjivanje generise `FOR UPDATE OF` osnovne tabele.
- `EXCLUDE` na `rezervacija` ostaje kao druga linija odbrane.
- Integracioni test: paralelno rezervacija + nedostupan period za isti datum -> prolazi tacno jedno.

### 5.1 Ture i termini (vise grupa na jednoj turi)

- Gost se prijavljuje na `TerminTure`, ne bira datume. Request sadrzi `terminId` + `brOsoba`.
- Servis popunjava: `termin`, `usluga = termin.tura`, `rezervacijaOd/Do` iz termina, `ukupnaCena = cenaPoOsobi * brOsoba`.
- Invarijanta: `termin != null` => `termin.tura.id == usluga.id`.
- Datumi termina su zatvoreni: `datumDo = datumOd + tura.brojDana - 1`. Tura 12.10-13.10 (2 dana) se cuva kao `[12.10, 13.10]`; jednodnevna tura 12.10 kao `[12.10, 12.10]`.
- `minBrOsoba` / `maksBrOsoba` kod ture vaze za **ceo termin**, ne za jednu grupu.
- Kapacitet: u transakciji, uz `PESSIMISTIC_WRITE` lock na termin (`TourDateRepo.findByIdForUpdate`):
  - `brPrijavljenih + brOsoba > tura.maksBrOsoba` -> HTTP `409`
  - inace `brPrijavljenih += brOsoba`
  - `brPrijavljenih >= minBrOsoba` -> termin `POTVRDJEN`
  - `brPrijavljenih == maksBrOsoba` -> termin `POPUNJEN`
- Otkazivanje / istek rezervacije: `brPrijavljenih -= brOsoba`, `POPUNJEN` -> `POTVRDJEN` (ili `OTVOREN` ako padne ispod minimuma pre `rokZaMin`).
- Kapacitet zauzimaju aktivni statusi: `CEKA_POTVRDU_VLASNIKA`, `CEKA_PLACANJE`, `ODOBRENO`.
- Termin sa prijavama se ne pomera; vlasnik ga otkazuje (`OTKAZAN` -> sve rezervacije `OTKAZANO_VLASNIK`, puni refund) i pravi novi.
- `rokZaMin` (`minGuestsDeadline`) zadaje vlasnik pri pravljenju termina kao broj dana pre pocetka (0..30): rok je ponoc (Europe/Belgrade) na pocetku dana `datumOd - brojDana`. Termin pocinje najranije sutra, a rok mora biti u buducnosti.
- Termin bez prijava se brise; ako ima samo stare (otkazane/istekle) rezervacije, postaje `OTKAZAN` umesto brisanja.

## 6) Transakcione granice

- Transakcije na service layer-u, ne u kontroleru.
- Komande (`create`, `approve`, `cancel`, `refund`) -> `@Transactional`.
- Read operacije -> `@Transactional(readOnly = true)`.
- Spoljne Stripe pozive ne drzati u dugoj DB transakciji.
- Mejlovi i ostali spoljni efekti idu **posle commita**, preko dogadjaja (sekcija 14), nikad direktno iz `@Transactional` metode.
- Webhook obrada: kratka transakcija (idempotency event + update `Placanje` + update `Rezervacija`).

## 7) Stripe integracija i security

> **Za sada nema Stripe-a** - ova sekcija je plan za buducnost. Kolone `stripe_payment_id` / `stripe_refund_id` postoje u modelu i ostaju `null` dok se integracija ne uvede.

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
- Pri svakom prelasku u status koji moze da istekne servis postavlja `Rezervacija.isticeU = now + timeout`; pri izlasku iz njega `isticeU = null`.
- Scheduler bira `WHERE status IN (...) AND istice_u < now()` (indeks `idx_rezervacija_status_istice`) i menja samo status.
- Smestaj: rezervisani termini se automatski oslobadjaju jer overlap blokira samo aktivne statuse.
- Tura: pri isteku rezervacije scheduler umanjuje `TerminTure.brPrijavljenih` (vidi 5.1).
- Tura: posle `TerminTure.rokZaMin`, ako je `brPrijavljenih < minBrOsoba` -> termin `OTKAZAN`, sve aktivne rezervacije `OTKAZANO_NEDOVOLJNO_PRIJAVA`, puni refund.
- Tura: termin posle `datumDo` -> `ZAVRSEN`.
- Konfigurabilno kroz `application.properties` (ne hardcode).
- ShedLock: preporuka da bude ukljucen odmah zbog multi-instance sigurnosti.

## 9) Brisanje i otkazivanje od strane vlasnika

### 9.1 Strategija brisanja

Soft delete postoji **samo za `Usluga`**, preko statusa: `status = OBRISANA`. Nema `deleted_at`, `@SQLDelete` ni `@Filter`.

- Na `Usluga` pokazuju rezervacije, placanja i recenzije, pa red mora ostati.
- `Smestaj` / `Tura` su isti red kao `Usluga` (JOINED, isti id), status na `Usluga` pokriva i njih.
- Upiti eksplicitno filtriraju status:
  - javna pretraga: `status = ODOBRENA` (obrisane su automatski iskljucene)
  - "moje usluge" kod vlasnika: `status <> OBRISANA`
  - rezervacije / placanja / recenzije: bez filtera, istorija ostaje citljiva
- `OBRISANA` je konacan status: nema prelaza iz njega, nove rezervacije se odbijaju.

Hard delete (red se stvarno brise):

| Entitet | Napomena |
|---|---|
| `UslugaSlika` | brise se i fajl iz storage-a (Cloudinary, preko `ImageStorage.delete`), posle commita brisanja reda |
| `Karakteristika` | samo admin; ako je u upotrebi -> `409` ili brisanje veza u `usluga_karakteristika` |
| `OmiljenaUsluga` | red postoji ili ne postoji, `UNIQUE (korisnik_id, usluga_id)`; dodavanje idempotentno |
| `NedostupanPeriod` | |
| `TerminTure` | samo bez aktivnih rezervacija; inace se otkazuje (9.2) |

Nikad se ne brisu: `Rezervacija`, `Placanje`, `Recenzija`, `UslugaIstorijaStatusa`, `ZahtevZaIzmenuUsluge`.

### 9.2 Vlasnik otkazuje jednu rezervaciju (ili termin ture)

- Dozvoljeno iz aktivnih statusa: `CEKA_POTVRDU_VLASNIKA`, `CEKA_PLACANJE`, `ODOBRENO`.
- Rezervacija -> `OTKAZANO_VLASNIK`, `isticeU = null`.
- Tura: `brPrijavljenih -= brOsoba` (vidi 5.1).
- Otkazivanje termina ture = otkazivanje svih njegovih aktivnih rezervacija + termin `OTKAZAN`.
- Refund: **100% uplacenog**, bez obzira na politiku (tabela u sekciji 3).
- Stripe refund i obavestenje gosta idu **posle commita**, van DB transakcije.

### 9.3 Vlasnik brise uslugu

Dva koraka, da vlasnik ne bi slucajno otkazao tudje rezervacije:

1. `DELETE /usluge/{id}`
   - nema aktivnih rezervacija (`status IN (CEKA_POTVRDU_VLASNIKA, CEKA_PLACANJE, ODOBRENO)`) -> `status = OBRISANA`, `204`
   - ima ih -> nista se ne menja, `409` + lista aktivnih rezervacija (frontend: "Brisanjem cete otkazati N rezervacija, gostima se vraca ceo iznos.")
2. `DELETE /usluge/{id}?otkaziRezervacije=true` (posle potvrde), jedna transakcija:
   - `findByIdForUpdate(id)` (lock, da u medjuvremenu ne nastane nova rezervacija)
   - sve aktivne rezervacije -> `OTKAZANO_VLASNIK` (9.2)
   - tura: svi buduci termini -> `OTKAZAN`
   - `usluga.status = OBRISANA`
   - posle commita: refundovi + obavestenja gostima

Alternativa za vlasnika koji ne mora odmah da obrise: `PAUZIRANA_VLASNIK` (nove rezervacije se ne primaju, postojece se odrzavaju), pa brisanje kad prodju.

### 9.4 Penali za vlasnika (v1: van sistema)

Odluka za v1: penali se **ne modeluju i ne obracunavaju automatski**. Admin ih resava rucno, van aplikacije.

Sistem samo cuva podatke na osnovu kojih admin odlucuje:

- broj rezervacija u statusu `OTKAZANO_VLASNIK` po vlasniku (izvodi se upitom, nista novo u modelu)
- (opciono) `Rezervacija.otkazanoAt` (`Instant`) i `razlogOtkazivanja` (`String`) - da se vidi koliko pre dolaska je otkazano i zasto
- admin pregled: lista vlasnika sa brojem otkazivanja u poslednjih N meseci

Kasnije (v2), ako se uvede automatika: pravila penala na nivou platforme (npr. otkazivanje < 7 dana pre dolaska -> X% vrednosti rezervacije), entitet `Penal` (vlasnik, rezervacija, iznos, status) i naplata preko Stripe Connect. Pre toga mora biti resen tok novca platforma <-> vlasnik.

## 10) DTO sloj i API granice

- Slike usluga se cuvaju na Cloudinary-ju (besplatni plan); u bazi samo `url` i `publicId`. Upload ide preko backenda (frontend nikad nema API secret), van DB transakcije. Skladiste je iza interfejsa `ImageStorage`.
- Slike i karakteristike vlasnik menja direktno u svakom statusu usluge, bez zahteva za izmenu.
- Gosti vide samo odobrene slike. Slika dodata javnoj usluzi (`ODOBRENA`, `PAUZIRANA_VLASNIK`) ceka pregled admina; slike usluge koja jos nije javna pregledaju se zajedno sa uslugom. Odbijena slika se brise iz storage-a, red ostaje sa razlogom za vlasnika. Admin moze i naknadno da obrise bilo koju sliku.

- Entitete ne izlagati direktno.
- Koristiti `Request DTO` + validaciju.
- Koristiti `Response DTO`.
- Mapiranje rucno, staticka metoda `from(...)` na response DTO-u (npr. `UserResponse.from(user)`). MapStruct se ne koristi.
- Za liste koristiti projekcije/light DTO gde je moguce.

## 11) Optimistic locking

Dodati `@Version` na:

- `Rezervacija` (vec dodato)
- `Usluga` (vec dodato; stiti `ocena` / `brojRecenzija` pri istovremenim recenzijama)
- `Placanje`
- `TerminTure` (vec dodato; stiti `brPrijavljenih` od overbookinga)

Minimum obavezno: `Rezervacija` (konkurentni update statusa) i `TerminTure`.

## 12) Enumi

Vazeca lista enuma i vrednosti je u sekciji 0 (kolona "Kod"). Pomocni skupovi u `BookingStatus`:

- `ACTIVE` = `AWAITING_OWNER_APPROVAL`, `AWAITING_PAYMENT`, `CONFIRMED` (zauzimaju datume / mesta)
- `EXPIRABLE` = `AWAITING_OWNER_APPROVAL`, `AWAITING_PAYMENT` (scheduler -> `EXPIRED`)
- `FINISHED` = svi zavrsni statusi

## 13) Redosled implementacije

Zavrseno:

- [x] model: entiteti, enumi, ogranicenja, indeksi, `@Version`, engleski nazivi
- [x] repozitorijumi (sekcija 15)
- [x] 1. Autentikacija: registracija, login, JWT filter, uloge (`USER`, `OWNER`, `ADMIN`), postani domacin, jezik.
- [x] 2. Usluge: vlasnik pravi smestaj / turu (slike, karakteristike, termini ture), admin odobrava, zahtevi za izmenu. Ostalo: pauziranje i brisanje usluge (9.3), radi se uz rezervacije.
- [x] 3. Pretraga i prikaz za goste (`ListingRepo` + `Specification`).
- [x] Nedostupni periodi (deo koraka 7): vlasnik blokira noci smestaja, ne preklapa se sa aktivnom rezervacijom, pod lockom usluge.

Sledece:

4. Rezervacije: kreiranje (lock + preklapanje / kapacitet), odobravanje, otkazivanje, refund pravila (sekcije 1.1, 3, 5, 9).
5. Scheduler: istek rezervacija, minimum za termine, zavrsavanje (sekcija 8).
6. Mejlovi preko dogadjaja posle commita (sekcija 14).
7. Recenzije (preracun `rating` / `reviewCount`), omiljene, nedostupni periodi.
8. Kasnije: Flyway (`EXCLUDE`, `lower(email)`), Stripe (sekcija 7), outbox za mejlove, penali.

Testovi uz svaki korak:

- unit testovi state machine-a i racunanja cene / refunda
- integracioni testovi overlap / concurrency (dve paralelne rezervacije, rezervacija + nedostupan period)
- scheduler expiry testovi

## 14) Notifikacije (email)

Mejl se salje **posle commita**, nikad unutar `@Transactional` metode:

- unutar transakcije bi drzao lock (SMTP traje sekundama, svi ostali gosti te usluge cekaju)
- mejl bi otisao i kad transakcija posle padne (rollback) -> gost dobije potvrdu za rezervaciju koja ne postoji
- pad mail servera bi rollback-ovao ispravnu rezervaciju

Obrazac:

1. Servis u transakciji samo objavi dogadjaj: `events.publishEvent(new BookingCreatedEvent(booking.getId()))`. U dogadjaj ide samo ID, ne entitet.
2. Listener: `@Async` + `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`; ucita sta mu treba i salje mejl.
3. `@EnableAsync` na aplikaciji.
4. Rollback -> listener se ne pokrece. Greska pri slanju -> samo log, rezervacija ostaje.

| Dogadjaj | Prima |
|---|---|
| `BookingCreated` | gost (zahtev poslat / potvrdjeno), domacin (nova rezervacija / ceka potvrdu) |
| `BookingApproved`, `BookingRejected` | gost |
| `BookingCancelled` | druga strana (gost ili domacin) |
| `BookingExpired` | gost |
| `TourDateConfirmed` (skupljen minimum) | svi gosti termina |
| `TourDateCancelled` (minimum nije skupljen / vlasnik otkazao) | svi gosti termina |
| `ListingApproved`, `ListingRejected` | vlasnik |

Ogranicenje: ako aplikacija padne izmedju commita i slanja, mejl se gubi (prihvatljivo za MVP). Kasnije: **outbox** tabela `email_outbox` upisana u istoj transakciji + scheduler koji salje i ponavlja.

## 15) Repozitorijumi

Paket `com.villagebooker.repo`, naziv `XxxRepo`. Metode se dodaju kad ih servis zatrazi, ne unapred.

| Repo | Za sta |
|---|---|
| `UserRepo` | login / registracija po email-u (`IgnoreCase`) |
| `ListingRepo` | `findByIdForUpdate` (lock, 5.0), javna lista po statusu, "moje usluge" bez `DELETED`, `JpaSpecificationExecutor` za pretragu sa filterima |
| `AccommodationRepo`, `TourRepo` | CRUD po podtipu |
| `TourDateRepo` | `findByIdForUpdate` (lock, 5.1), termini ture (svi / samo `OPEN`+`CONFIRMED` za goste), scheduler: prosao `minGuestsDeadline`, zavrseni termini |
| `BookingRepo` | rezervacije gosta / vlasnika (sa `@EntityGraph`), `existsOverlapping`, scheduler: istekle (`EXPIRABLE`) i zavrsene (`CONFIRMED` + `endDate < danas`), aktivne po usluzi / terminu |
| `UnavailablePeriodRepo` | periodi usluge, `existsOverlapping` |
| `PaymentRepo` | placanja rezervacije, `findByStripePaymentId` (buducnost) |
| `ReviewRepo` | po rezervaciji, recenzije usluge, `averageRatingForListing` + `countByBookingListingId` za preracun `rating` / `reviewCount` |
| `FavoriteRepo` | lista korisnika (sa `listing`), `exists...`, `delete...` (idempotentno, unutar `@Transactional`) |
| `ListingImageRepo`, `ListingStatusHistoryRepo`, `ListingChangeRequestRepo`, `FeatureRepo` | slike po redosledu, istorija, admin red zahteva, katalog karakteristika |

Pravila:

- Liste koje se mapiraju u DTO sa podacima iz veze (npr. naziv usluge uz rezervaciju) koriste `@EntityGraph`, inace N+1.
- Liste koje rastu (rezervacije, recenzije, omiljene, istorija) uvek sa `Pageable`.
- `findByIdForUpdate` i `delete...` metode se pozivaju samo iz `@Transactional` servisa.
