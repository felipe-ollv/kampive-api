-- Free-text location supplied for review; official geography is linked by the reviewer.
ALTER TABLE kampive.campsite ADD COLUMN IF NOT EXISTS city_name varchar(120) NOT NULL DEFAULT '';
