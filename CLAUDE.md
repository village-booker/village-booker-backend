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

Podela po slojevima (ne po feature-ima). Nove klase idu u odgovarajući sloj:

- `controller`: REST kontroleri (`AuthController`, `UserController`...).
- `service`: poslovna logika i transakcije (`AuthService`, `UserService`...).
- `dto`: request / response record-i, podeljeni po oblasti u podpakete (`dto.auth`, `dto.user`, kasnije `dto.listing`, `dto.booking`...).
- `repo`: Spring Data repository-ji (`XxxRepo`).
- `model`: JPA entiteti (`User`, `Listing` sa podklasama `Accommodation` i `Tour`, `TourDate`, `Booking`, `Payment`, `Review`, `UnavailablePeriod`, `Favorite`, `Feature`, `ListingImage`, `ListingHistory`, `ListingChangeRequest`). `enums`: enumi.
- `security`: JWT i Spring Security infrastruktura (`JwtService`, `JwtAuthFilter`, `AuthUser`, `SecurityProblemHandler`).
- `config`: konfiguracija (`SecurityConfig`, `JwtProperties`, `TermsProperties`).
- `common`: `bank` (`BankAccountNumber`), `exception` (`ErrorCode`, `ApiException` i njene podklase `NotFoundException`, `ConflictException`, `BadRequestException`, `ForbiddenException`, `GlobalExceptionHandler`) i `time` (`AppTime`, `Clock` bean).
- Testovi prate isti raspored paketa.

## Konvencije modela

- Trenutak događaja (`createdAt`, `expiresAt`, `freeCancellationUntil`...) je `Instant` (`timestamptz`). Datumi boravka i termina su `LocalDate`. Lokalni datum u trenutak uvek preko `AppTime.ZONE` (`Europe/Belgrade`).
- Trenutno vreme: u servisima nikad `Instant.now()` / `LocalDate.now()`, nego ubačeni `Clock`: `Instant.now(clock)`, `AppTime.today(clock)`, `AppTime.startOfDay(date)`. Server radi u UTC-u, a testovi menjaju sat sa `Clock.fixed(...)`.
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

## Auth i uloge

- Uloge: `USER` (gost), `OWNER` (domaćin), `ADMIN`. Hijerarhija u `SecurityConfig.roleHierarchy`: `OWNER > USER`, `ADMIN > USER` (admin nije domaćin). `hasRole('USER')` prolazi i za `OWNER` i `ADMIN`.
- Registracija uvek pravi `USER`; request DTO nema polje `role` i nikad ga ne sme imati. `USER -> OWNER` ide kroz "Postani domaćin": `POST /api/users/me/become-host` sa `bankAccount` i `acceptHostTerms: true`, vraća novi token (isti oblik kao login). Uloga se proverava iz baze, ne iz tokena: već domaćin -> 409, admin -> 403. Račun je srpski format `XXX-XXXXXXXXXXXXX-XX` sa kontrolnim brojem po modulu 97 (`common.bank.BankAccountNumber`); prima se i skraćen srednji deo ili 18 cifara bez crtica, a čuva se uvek pun oblik. Admin se postavlja ručno u bazi: `UPDATE app_user SET role = 'ADMIN' WHERE email = '...'`.
- Uslovi korišćenja: registracija traži `acceptTerms: true` i upisuje `termsVersion` / `termsAcceptedAt`; domaćin isto za `hostTermsVersion` / `hostTermsAcceptedAt`. Trenutne verzije su `app.terms.version` i `app.terms.host-version`.
- JWT (HS256, `JWT_SECRET` iz `.env`, trajanje `app.jwt.expiration`) nosi `sub` = id, `email`, `role`. Filter ne ide u bazu; u kontroleru: `@AuthenticationPrincipal AuthUser user`. Promena uloge važi tek sa novim tokenom.
- Javno: `POST /api/auth/register`, `POST /api/auth/login`; sve ostalo traži token. Nove javne rute se dodaju u `SecurityConfig`.
- Trajanje tokena 24h, bez refresh tokena; odjava = frontend briše token. CORS origin-i za produkciju se dodaju kad bude poznat domen.
- Lozinke: BCrypt (max 72 bajta). Login za nepostojeći email i pogrešnu lozinku vraća isti 401.
- BCrypt (`encode` / `matches`, ~100 ms) se nikad ne izvršava unutar `@Transactional` metode, jer bi za to vreme držao konekciju iz pool-a. Zato `AuthService.register` / `login` namerno nemaju `@Transactional`.
- Email se uvek čuva i traži malim slovima (`AuthService` normalizuje), pa `UserRepo` koristi `findByEmail` / `existsByEmail`, ne `...IgnoreCase` (koji ne koristi indeks).
- Odluka: registracija sa već postojećim emailom vraća 409 "Email is already registered." (korisnik mora da zna zašto ne može da se registruje). Posledica: napadač može da proverava koji su emailovi registrovani. Prihvaćeno za MVP; ako bude potrebno, menja se u opšti odgovor + mejl vlasniku naloga.
- Poznato ograničenje: obrisan ili blokiran korisnik zadržava pristup do isteka tokena (24h), jer filter ne ide u bazu. Kad se uvede blokiranje naloga, odlučiti da li se token proverava u bazi.
- Dozvoljeni frontend origin-i: `app.cors.allowed-origins`.

## Oglasi (usluge)

- Rute: domaćin `/api/owner/listings` (`OwnerListingController`, `hasRole('OWNER')`), javna pretraga kasnije `/api/listings`, admin `/api/admin/listings`.
- Domaćin: `POST /accommodations`, `POST /tours` (novi oglas je uvek `PENDING`; `status`, `rating`, `reviewCount` nikad ne dolaze iz request-a), `GET` "moji oglasi" (sve osim `DELETED`, najnoviji prvi, `?page=&size=` do 100, odgovor `PageResponse`), `GET /{id}` (tuđi oglas -> 403 `NOT_LISTING_OWNER`, nepostojeći ili `DELETED` -> 404).
- Request-ovi za smeštaj i turu su ravni JSON objekti i implementiraju zajednički interfejs `ListingRequest`, pa se zajednička pravila proveravaju jednom (`OwnerListingService.checkCommonRules`).
- Pravila koja važe za sve oglase: `minGuests <= maxGuests`; smeštaj još `minGuests <= baseGuests <= maxGuests`. Otkazivanje: `FREE` traži `freeCancellationDays` i ne sme imati `refundPercentage`; `PARTIAL` traži `refundPercentage`, a `freeCancellationDays` je opcion; `NON_REFUNDABLE` nema ni jedno ni drugo. Kapara (`depositPercentage > 0`) nije dozvoljena uz `ON_SITE`.
- `ListingResponse` ima `accommodation` ili `tour` (tačno jedno popunjeno, prema `type`). `ListingSummaryResponse.price` je cena noćenja (smeštaj) ili cena po osobi (tura).
- `district` je za sada slobodan tekst (šifarnik je otvorena odluka).

## Rezervacije (sažetak spec-a)

- Snimak uslova: cena, politika otkazivanja, `freeCancellationUntil`, procenti i način plaćanja kopiraju se sa `Listing` na `Booking` samo u servisu pri kreiranju, nikad iz request DTO-a, i posle se ne menjaju. Logika otkazivanja i refunda čita samo polja sa rezervacije.
- Smeštaj: pre provere dostupnosti zaključati red oglasa (`ListingRepo` metoda sa `PESSIMISTIC_WRITE`), proveriti i rezervacije i `UnavailablePeriod`, pa insert. Konflikt vraća HTTP 409.
- Ture: gost se prijavljuje na `TourDate`; kapacitet se menja pod lockom na terminu (`TourDateRepo` metoda sa `PESSIMISTIC_WRITE`).
- Statusi i prelazi su definisani u spec-u; ne uvoditi nove statuse bez izmene spec-a.

## Slojevi i API

- Controller je tanak: prima request DTO (Bean Validation), vraća response DTO. Entiteti nikad ne izlaze iz servisa.
- `@Transactional` na servisu; čitanja sa `readOnly = true`. Spoljni pozivi (email, plaćanje) ne idu unutar duge transakcije; email se šalje posle commit-a.
- Uloge se proveravaju sa `@PreAuthorize`, a vlasništvo (vlasnik menja samo svoj oglas) u servisu.
- Greške se vraćaju kao `ProblemDetail` iz jednog `@RestControllerAdvice` (`common.exception.GlobalExceptionHandler`). Servis baca `NotFoundException` (404), `ConflictException` (409), `BadRequestException` (400, prekršeno poslovno pravilo) ili `ForbiddenException` (403, nije vlasnik); ne pravi `ResponseEntity` sa greškom ručno. Detalji iz baze i stack trace idu samo u log, nikad klijentu.
- Jezik (dugme SR / EN): backend ne prevodi ništa, prevodi frontend. Zato svaka greška ima stabilan `code` iz enuma `ErrorCode` (`ApiException` je baza svih poslovnih izuzetaka: `new ConflictException(ErrorCode.X, "English detail")`, opciono i `params` za vrednosti u prevedenom tekstu). `detail` je na engleskom i služi programerima. Validacija vraća `errors: {polje: {code: "Size", params: {min, max}, message}}`, gde je `code` ime anotacije. Novo poslovno pravilo = nov kod u `ErrorCode`; postojeći kod se ne preimenuje kad ga frontend koristi. Enumi (statusi i sl.) idu kao kodovi i frontend ih prevodi.
- Izabrani jezik se čuva na korisniku (`User.language`, `SR` podrazumevano): opciono polje `language` pri registraciji, `PUT /api/users/me/language`, vraća se u `UserResponse`. Koristi se za mejlove i da bi izbor pratio korisnika na drugim uređajima. Sadržaj koji pišu korisnici (opisi oglasa, recenzije) se ne prevodi.

## Otvorene odluke (ne implementirati bez dogovora tima)

Okrug kao šifarnik, naziv kapara ili avans, online plaćanje i provajder, plan i provizija domaćina, verifikacija telefona. Trenutno stanje je u planskom dokumentu.

## Git

- Ne raditi direktno na `main`. Rad ide na `develop`.
- Ne commitovati `.env`, `build/`, `.gradle-local/` ni IDE fajlove.
- Ako izmena menja model ili pravila iz ovog fajla, ažurirati i ovaj fajl (i spec, ako se tiče rezervacija) u istom commitu.
