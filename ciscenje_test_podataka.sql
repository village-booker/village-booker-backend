-- Brise ostatke testova iz baze: korisnike test-<uuid>@example.com i sve sto je vezano za njih.
-- Pravi korisnici nemaju takav email. Pokreni ceo fajl odjednom (jedna transakcija).
-- Prvo pogledaj koliko ih ima:
--   select count(*) from app_user where email like 'test-%@example.com';

begin;

create temp table tu on commit drop as
    select id from app_user where email like 'test-%@example.com';
create temp table tl on commit drop as
    select id from listing where owner_id in (select id from tu);
create temp table tb on commit drop as
    select id from booking where listing_id in (select id from tl) or guest_id in (select id from tu);
create temp table tr on commit drop as
    select id from review where booking_id in (select id from tb);

delete from email_message where recipient_id in (select id from tu);
delete from review_report where review_id in (select id from tr)
    or reporter_id in (select id from tu) or resolved_by in (select id from tu);
delete from review where id in (select id from tr);
delete from payment where booking_id in (select id from tb);
delete from booking where id in (select id from tb);
delete from favorite where user_id in (select id from tu) or listing_id in (select id from tl);
delete from unavailable_period where listing_id in (select id from tl);
delete from tour_date where tour_id in (select id from tl);
delete from listing_image where listing_id in (select id from tl);
delete from listing_feature where listing_id in (select id from tl);
delete from listing_change_request where listing_id in (select id from tl)
    or requester_id in (select id from tu) or admin_id in (select id from tu);
delete from listing_status_history where listing_id in (select id from tl) or changed_by_id in (select id from tu);
delete from accommodation where listing_id in (select id from tl);
delete from tour where listing_id in (select id from tl);
delete from listing where id in (select id from tl);
delete from app_user where id in (select id from tu);

commit;
