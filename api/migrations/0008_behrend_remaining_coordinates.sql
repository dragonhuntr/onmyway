-- Coordinates from OpenStreetMap for the buildings 0004 left hand-placed, using the
-- footprint's Overpass `out center` the same way 0004 did for Junker, Ohio and Trippe.
--
-- Burke Center is the Jack D. Burke Research and Economic Development Center (named in
-- 2006), so 'burke' and 'rdc' are the same building at 5101 Jordan Road. The OSM footprint
-- has no name but carries that address and contains the "School of Engineering" and
-- "Clark Cafe" nodes. Both rows move there: 'burke' about 140 m, 'rdc' about 330 m.
--
-- Nick, Otto Behrend Science, Kochel and Hammermill aren't named or otherwise identifiable
-- in OSM and keep their hand-placed coordinates from 0003.

-- OSM way 867467677 (building=yes, addr 5101 Jordan Road)
UPDATE buildings SET lat = 42.11869, lng = -79.97983 WHERE id = 'burke';
-- OSM way 867467677 (same building as 'burke')
UPDATE buildings SET lat = 42.11869, lng = -79.97983 WHERE id = 'rdc';
