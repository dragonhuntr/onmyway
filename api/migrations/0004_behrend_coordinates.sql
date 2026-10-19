-- Coordinates from OpenStreetMap for the buildings it names. Ohio and Trippe Halls were
-- about 500 m off, Dobbins about 200 m. Burke, Nick, Otto Behrend Science, Kochel,
-- Hammermill and the Research and Economic Development Center aren't named in OSM and
-- keep their hand-placed coordinates from 0003.

UPDATE buildings SET lat = 42.11937, lng = -79.98375 WHERE id = 'reed';
UPDATE buildings SET lat = 42.11816, lng = -79.98254 WHERE id = 'dobbins';
UPDATE buildings SET lat = 42.12027, lng = -79.98164 WHERE id = 'lilley';
UPDATE buildings SET lat = 42.12013, lng = -79.97814 WHERE id = 'junker';
UPDATE buildings SET lat = 42.11646, lng = -79.98494 WHERE id = 'ohio';
UPDATE buildings SET lat = 42.11560, lng = -79.98449 WHERE id = 'trippe';
