-- Apply manually after campsite-community.sql. No automatic DDL at application startup.
BEGIN;
ALTER TABLE kampive.app_user ADD COLUMN uuid_city UUID REFERENCES kampive.city(uuid_city);
ALTER TABLE kampive.app_user ADD COLUMN travel_styles TEXT[] NOT NULL DEFAULT '{}'
    CHECK (cardinality(travel_styles)<=4 AND travel_styles <@ ARRAY['tent','motorhome','trailer','chalet']::text[]);
GRANT UPDATE (name,email,phone_number,photo_url,bio,uuid_country,uuid_city,uuid_adventurer_style,
    travel_styles,notifications_enabled,email_verified_at,phone_verified_at,deactivated_at) ON kampive.app_user TO kampive_app;
GRANT DELETE ON kampive.user_credential,kampive.social_identity,kampive.favorite,kampive.user_notification TO kampive_app;
GRANT UPDATE (evidence) ON kampive.campsite_claim TO kampive_app;
COMMIT;
