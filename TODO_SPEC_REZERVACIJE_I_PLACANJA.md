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
- broj noci / dana -> `Rezervacija.getBrojNoci()` = `DAYS.between(rezervacijaOd, rezervacijaDo)`

## 1.2 Konvencije modela

- **Vreme:** trenutak dogadjaja (`kreiranAt`, `vremeRezervisanja`, `isticeU`, `besplatnoOtkazivanjeDo`, `rokZaMin`, `vreme` placanja...) je `Instant` -> `timestamptz`. Datumi boravka/termina (`rezervacijaOd/Do`, `datumOd/Do`) su `LocalDate`. Konverzija lokalnog datuma u trenutak uvek sa `ZoneId.of("Europe/Belgrade")`.
- **Intervali datuma** su poluotvoreni `[od, do)` svuda: `Rezervacija`, `NedostupanPeriod`, `TerminTure`. Svaka tabela ima `CHECK od < do`.
- **Novac:** `BigDecimal`, `numeric(19,2)`. **Procenti:** `numeric(5,2)` + `CHECK 0..100`. **Koordinate:** `numeric(9,6)`. `BigDecimal` bez `precision/scale` je zabranjen (Hibernate default je `numeric(38,2)`).
- **Imena:** camelCase u Javi, snake_case samo u `@Column(name = ...)`.
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

- Datumski interval je poluotvoren: `[rezervacija_od, rezervacija_do)`.
- DB `CHECK`: `rezervacija_od < rezervacija_do`.
- PostgreSQL `EXCLUDE` constraint po `usluga_id` + overlap, samo za aktivne statuse:

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
- `UslugaRepository.findByIdForUpdate(id)` sa `@Lock(PESSIMISTIC_WRITE)`.
- Pod lockom: proveri preklapanje u **obe** tabele -> insert -> commit. Konflikt -> HTTP `409`.
- Transakcija pod lockom mora biti kratka (bez Stripe poziva).
- `EXCLUDE` na `rezervacija` ostaje kao druga linija odbrane.
- Integracioni test: paralelno rezervacija + nedostupan period za isti datum -> prolazi tacno jedno.

### 5.1 Ture i termini (vise grupa na jednoj turi)

- Gost se prijavljuje na `TerminTure`, ne bira datume. Request sadrzi `terminId` + `brOsoba`.
- Servis popunjava: `termin`, `usluga = termin.tura`, `rezervacijaOd/Do` iz termina, `ukupnaCena = cenaPoOsobi * brOsoba`.
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
| `UslugaSlika` | brise se i fajl iz storage-a |
| `Karakteristika` | samo admin; ako je u upotrebi -> `409` ili brisanje veza u `usluga_karakteristika` |
| `OmiljenaUsluga` | red postoji ili ne postoji, `UNIQUE (korisnik_id, usluga_id)` (model jos ima `status`/`datumBrisanja` - za uklanjanje) |
| `NedostupanPeriod` | |
| `TerminTure` | samo bez aktivnih rezervacija; inace se otkazuje (9.2) |

Nikad se ne brisu: `Rezervacija`, `Placanje`, `Recenzija`, `UslugaIstorija`.

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

- Entitete ne izlagati direktno.
- Koristiti `Request DTO` + validaciju.
- Koristiti `Response DTO`.
- Mapiranje preko MapStruct.
- Za liste koristiti projekcije/light DTO gde je moguce.

## 11) Optimistic locking

Dodati `@Version` na:

- `Rezervacija` (vec dodato)
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
- `NacinPlacanja`: `NA_LICU_MESTA`, `ONLINE`, `KOMBINOVANO` (na `Usluga` i `Rezervacija`)
- `NacinUplate`: `ONLINE`, `NA_LICU_MESTA` (na `Placanje`)
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
9. Brisanje usluge preko statusa + otkazivanje od strane vlasnika (sekcija 9).
10. DTO + MapStruct sloj.
11. Testovi:
- unit testovi state machine-a
- integracioni testovi overlap/concurrency
- webhook idempotency testovi
- scheduler expiry testovi
