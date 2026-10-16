# Village Booker backend

Platforma koja povezuje ljude iz grada sa seoskim domaćinstvima: smeštaj i ture (rafting, planinarenje...), pretraga, rezervacije, recenzije, moderacija od strane admina. Ovaj repo je REST backend; frontend je poseban projekat.

Jezik: kod (klase, polja, tabele, kolone, komentari u kodu) je na engleskom. Spec, dokumentacija, commit poruke i razgovor su na srpskom (latinica). Rečnik srpski naziv -> engleski naziv je u sekciji 0 spec-a.

## Stack i pokretanje

- Java 21, Spring Boot 4, Gradle, Spring Data JPA (Hibernate), Spring Security + JWT, PostgreSQL (Neon), Lombok.
- Tajne idu u `.env` u korenu repoa (napravi ga po uzoru na `.env.example`). `.env` se nikad ne commituje. `application.properties` sme da sadrži samo `${...}` reference.
- Pokretanje: `./gradlew bootRun`. Testovi: `./gradlew test`. Pre push-a: `./gradlew build`.
- Trenutno: `spring.jpa.hibernate.ddl-auto=update` i Flyway isključen. To je privremeno, za lokalni razvoj. Pre zajedničke baze prelazi se na Flyway `V1` migraciju + `ddl-auto=validate`. Ne dodavati migracije dok tim to ne dogovori.

## Izvori istine

- `TODO_SPEC_REZERVACIJE_I_PLACANJA.md`: obavezno pročitati pre bilo kakvog rada na rezervacijama, plaćanju, otkazivanju, statusima, terminima tura ili scheduleru. Spec ima prednost nad pretpostavkama.
- Planski dokument (model, arhitektura, plaćanje, otvorena pitanja): https://claude.ai/code/artifact/7302cafd-6a45-4c39-822e-28bbc23b7eca
- Dijagram klasa: `dijagram_klasa.drawio` u deljenom Google Drive folderu VillageBooker (može da kasni za kodom; kod i spec imaju prednost).
- Funkcionalni zahtevi FZ-01 do FZ-21: formalna dokumentacija (Word na OneDrive-u).

## Struktura

- `com.villagebooker.model`: JPA entiteti (`User`, `Listing` sa podklasama `Accommodation` i `Tour`, `TourDate`, `Booking`, `Payment`, `Review`, `UnavailablePeriod`, `Favorite`, `Feature`, `ListingImage`, `ListingHistory`, `ListingChangeRequest`).
- `com.villagebooker.enums`: enumi. `com.villagebooker.repo`: Spring Data repository-ji.
- Cilj (dogovara se): organizacija po feature-ima (`auth`, `user`, `listing`, `search`, `booking`, `review`, `moderation`, `notification`, `common`), svaki sa svojim controllerom, servisom, repository-jem i DTO-ima. Nove pakete praviti po tom principu.

## Konvencije modela

- Trenutak događaja (`createdAt`, `expiresAt`, `freeCancellationUntil`...) je `Instant` (`timestamptz`). Datumi boravka i termina su `LocalDate`. Lokalni datum u trenutak uvek preko `ZoneId.of("Europe/Belgrade")`.
- Intervali datuma su **zatvoreni** `[startDate, endDate]` svuda (`Booking`, `UnavailablePeriod`, `TourDate`), uz `CHECK start <= end`. Svaki datum u intervalu je jedan zauzet dan.
    - Smeštaj: datum = noć koja počinje tog dana; `endDate` je poslednja noć, dan odlaska = `endDate + 1` (ne čuva se). Dolazak 12.10, odlazak 14.10 -> `[12.10, 13.10]`.
    - Tura: datum = dan ture.
    - Preklapanje: `a.start <= b.end AND b.start <= a.end`.
    - Broj dana: `DAYS.between(start, end) + 1`. Pazi na `+ 1`.
- Novac: `BigDecimal`, `precision = 19, scale = 2`. Procenti: `precision = 5, scale = 2` + `CHECK 0..100`. Koordinate: `precision = 9, scale = 6`. `BigDecimal` bez preciznosti nije dozvoljen.
- camelCase u Javi, snake_case u `@Column(name = ...)`; tabele snake_case u jednini. Ime polja i ime kolone treba da kažu isto.
- Svaka FK kolona po kojoj se pretražuje ima `@Index`.
- `CHECK` ograničenja preko `@Table(check = @CheckConstraint(...))`. Ono što Hibernate ne ume (`EXCLUDE`, `UNIQUE (lower(email))`, parcijalni indeksi) ide u Flyway migraciju.
- Veze: `@ManyToOne(fetch = LAZY)` uvek. Ne dodavati `@OneToMany` kolekcije osim kad roditelj upravlja životom dece. Za liste se pišu upiti u repository-ju, sa paginacijom.
- Na entitetima ne koristiti `@Data`, `@ToString` ni `@EqualsAndHashCode` (rekurzija i učitavanje lazy veza).
- Vrednosti koje se izvode ne čuvaju se (vlasnik rezervacije je `booking.listing.owner`, broj dana je `getDayCount()`).

## Rezervacije (sažetak spec-a)

- Snimak uslova: cena, politika otkazivanja, `freeCancellationUntil`, procenti i način plaćanja kopiraju se sa `Listing` na `Booking` samo u servisu pri kreiranju, nikad iz request DTO-a, i posle se ne menjaju. Logika otkazivanja i refunda čita samo polja sa rezervacije.
- Smeštaj: pre provere dostupnosti zaključati red oglasa (`ListingRepo` metoda sa `PESSIMISTIC_WRITE`), proveriti i rezervacije i `UnavailablePeriod`, pa insert. Konflikt vraća HTTP 409.
- Ture: gost se prijavljuje na `TourDate`; kapacitet se menja pod lockom na terminu (`TourDateRepo` metoda sa `PESSIMISTIC_WRITE`).
- Statusi i prelazi su definisani u spec-u; ne uvoditi nove statuse bez izmene spec-a.

## Slojevi i API

- Controller je tanak: prima request DTO (Bean Validation), vraća response DTO. Entiteti nikad ne izlaze iz servisa.
- `@Transactional` na servisu; čitanja sa `readOnly = true`. Spoljni pozivi (email, plaćanje) ne idu unutar duge transakcije; email se šalje posle commit-a.
- Uloge se proveravaju sa `@PreAuthorize`, a vlasništvo (vlasnik menja samo svoj oglas) u servisu.
- Greške se vraćaju kao `ProblemDetail` iz jednog `@RestControllerAdvice`.

## Otvorene odluke (ne implementirati bez dogovora tima)

Okrug kao šifarnik, naziv kapara ili avans, online plaćanje i provajder, plan i provizija domaćina, verifikacija telefona. Trenutno stanje je u planskom dokumentu.

## Git

- Ne raditi direktno na `main`. Rad ide na `develop`.
- Ne commitovati `.env`, `build/`, `.gradle-local/` ni IDE fajlove.
- Ako izmena menja model ili pravila iz ovog fajla, ažurirati i ovaj fajl (i spec, ako se tiče rezervacija) u istom commitu.
