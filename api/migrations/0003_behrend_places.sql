-- Penn State Behrend buildings and dining.
-- Coordinates are approximate (hand-placed, not surveyed) and only feed walking-time
-- estimates; correct them from a map before relying on detour numbers.
-- Which building each restaurant sits in is also unconfirmed except Bruno's (Reed).

INSERT INTO buildings (id, name, lat, lng) VALUES
  ('reed',       'Reed Union Building',          42.11957, -79.98348),
  ('dobbins',    'Dobbins Hall',                 42.11903, -79.98494),
  ('burke',      'Burke Center',                 42.11841, -79.98143),
  ('nick',       'Nick Building',                42.11779, -79.98196),
  ('obs',        'Otto Behrend Science Building',42.11818, -79.98283),
  ('lilley',     'Lilley Library',               42.11887, -79.98232),
  ('kochel',     'Kochel Center',                42.11964, -79.98199),
  ('junker',     'Junker Center',                42.12149, -79.98057),
  ('hammermill', 'Hammermill Building',          42.11741, -79.98277),
  ('trippe',     'Trippe Hall',                  42.12038, -79.98591),
  ('ohio',       'Ohio Hall',                    42.12100, -79.98420),
  ('rdc',        'Research and Economic Development Center', 42.11583, -79.97904);

INSERT INTO restaurants (id, name, transact_id, building_id, opens_min, closes_min, sort) VALUES
  ('clarks', 'Clark’s Cafe', 2367, 'burke',   450, 900,  0),
  ('paws',   'Paws',         2368, 'dobbins', 450, 1080, 1),
  ('brunos', 'Bruno’s',      2366, 'reed',    630, NULL, 2);
