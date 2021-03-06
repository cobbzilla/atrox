package histori.wiki.finder;

import histori.model.support.LatLon;
import histori.wiki.ParsedWikiArticle;
import histori.wiki.WikiNode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;
import org.cobbzilla.util.math.Cardinal;

import java.util.List;

import static histori.wiki.finder.InfoboxNames.BOXNAME_COORD;
import static histori.wiki.finder.InfoboxNames.isCoordinateInfoboxCandidate;
import static org.cobbzilla.util.daemon.ZillaRuntime.die;
import static org.cobbzilla.util.daemon.ZillaRuntime.empty;

@NoArgsConstructor @Accessors(chain=true) @Slf4j
public class LocationFinder extends FinderBase<LatLon> {

    public static final String ATTR_COORDINATES = "coordinates";

    public static final String ATTR_LATITUDE = "latitude";
    public static final String ATTR_LONGITUDE = "longitude";

    public static final String ATTR_LAT_D = "lat_d";
    public static final String ATTR_LAT_M = "lat_m";
    public static final String ATTR_LAT_S = "lat_s";
    public static final String ATTR_LAT_NS = "lat_NS";
    public static final String ATTR_LONG_D = "long_d";
    public static final String ATTR_LONG_M = "long_m";
    public static final String ATTR_LONG_S = "long_s";
    public static final String ATTR_LONG_EW = "long_EW";

    public static final String ATTR_LATD = ATTR_LAT_D.replace("_", "");
    public static final String ATTR_LATM = ATTR_LAT_M.replace("_", "");
    public static final String ATTR_LATS = ATTR_LAT_S.replace("_", "");
    public static final String ATTR_LATNS = ATTR_LAT_NS.replace("_", "");
    public static final String ATTR_LONGD = ATTR_LONG_D.replace("_", "");
    public static final String ATTR_LONGM = ATTR_LONG_M.replace("_", "");
    public static final String ATTR_LONGS = ATTR_LONG_S.replace("_", "");
    public static final String ATTR_LONGEW = ATTR_LONG_EW.replace("_", "");

    public static final String ATTR_LAT_DEG = "lat_deg";
    public static final String ATTR_LAT_MIN = "lat_min";
    public static final String ATTR_LAT_SEC = "lat_sec";
    public static final String ATTR_LAT_DIR = "lat_dir";
    public static final String ATTR_LON_DEG = "lon_deg";
    public static final String ATTR_LON_MIN = "lon_min";
    public static final String ATTR_LON_SEC = "lon_sec";
    public static final String ATTR_LON_DIR = "lon_dir";

    public static final String ATTR_PLACE = "place";

    public static final String BATTLE_PREFIX = "battle of ";

    public String getLocationNameFromTitle(ParsedWikiArticle art) {
        if (art.getName().toLowerCase().startsWith(BATTLE_PREFIX)) {
            return art.getName().substring(BATTLE_PREFIX.length()).trim();
        }
        return null;
    }

    @Override public LatLon find() { return find(article, false); }

    public LatLon find(ParsedWikiArticle art, boolean coordinatesOnly) {
        // todo: try all of these strategies in parallel? some may be long-running
        LatLon latLon;
        try {
            latLon = parseCoordinates(art);
            if (latLon != null) return latLon;
        } catch (Exception e) {
            log.warn("Attempt at gleaning coordinates from infobox failed: " + e);
        }
        if (coordinatesOnly) return null;

        try {
            latLon = parseTitle(art);
            if (latLon != null) return latLon;
        } catch (Exception e) {
            log.warn("Attempt at gleaning coordinates based on article title failed: " + e);
        }

        try {
            latLon = parsePlace(art);
            if (latLon != null) return latLon;
        } catch (Exception e) {
            log.warn("Attempt at gleaning coordinates based on a place name failed: " + e);
        }
        return null;
    }

    private LatLon parseTitle(ParsedWikiArticle art) {

        final String loc = getLocationNameFromTitle(art);
        if (loc == null) {
            log.warn("no location could be gleaned from title");
            return null;
        }

        final ParsedWikiArticle locArticle = getWiki().find(loc);
        if (locArticle == null) {
            log.warn("location gleaned from title was "+loc+" but locArticle was not found");
            return null;
        }

        return find(locArticle, true);
    }

    private LatLon parsePlace(ParsedWikiArticle art) {
        for (WikiNode box : art.getInfoboxes()) {
            final WikiNode placeAttr = box.findChildNamed(ATTR_PLACE);
            if (placeAttr != null) {
                // does it have a nested Coord box?
                final WikiNode coordBox = placeAttr.findFirstInfoboxWithName(BOXNAME_COORD);
                if (coordBox != null) {
                    try {
                        LatLon latLon = parseCoordinatesBox(coordBox);
                        if (latLon != null) return latLon;
                    } catch (Exception e) {
                        log.warn("Error parsing Coord box within Place attribute: "+e);
                    }
                }

                final List<WikiNode> links = placeAttr.getLinks();

                // try the first link
                ParsedWikiArticle placeArticle;
                if (!links.isEmpty()) {
                    final String linkName = links.get(0).getName();
                    placeArticle = getWiki().find(linkName);
                    if (placeArticle != null) {
                        LatLon latLon = find(placeArticle, true);
                        if (latLon != null) return latLon;
                    }
                }

                // try the entire place text
                final String placeName = placeAttr.findAllChildText();
                placeArticle = getWiki().find(placeName);
                if (placeArticle != null) {
                    LatLon latLon = find(placeArticle, true);
                    if (latLon != null) return latLon;
                }

                // tokenize by commas and try each combo from shortest to longest
                if (placeName.contains(",")) {
                    final String[] placeParts = placeName.split(",");
                    final StringBuilder nameBuilder = new StringBuilder();
                    for (String part : placeParts) {
                        if (nameBuilder.length() > 0) nameBuilder.append(", ");
                        nameBuilder.append(part.trim());
                        placeArticle = getWiki().find(nameBuilder.toString());
                        if (placeArticle != null) {
                            LatLon latLon = find(placeArticle, true);
                            if (latLon != null) return latLon;
                        }
                    }
                }

            }
        }
        return die("parsePlace: not found");
    }

    private LatLon parseCoordinates(ParsedWikiArticle art) {

        List<WikiNode> infoboxes;
        LatLon latLon;

        infoboxes = art.getInfoboxes();
        latLon = findCoordinates(infoboxes);
        if (latLon != null) return latLon;

        infoboxes = art.getInfoboxesRecursive();
        latLon = findCoordinates(infoboxes);
        if (latLon != null) return latLon;

        return die("parseCoordinates: not found");
    }

    private LatLon findCoordinates(List<WikiNode> infoboxes) {
        for (WikiNode box : infoboxes) {
            if (!isCoordinateInfoboxCandidate(box.getName())) continue;

            if (box.getName().equalsIgnoreCase(BOXNAME_COORD)) {
                try {
                    return fromCoordsValue(box.getChildren());
                } catch (Exception e) {
                    log.warn("Error parsing coords box: "+e, e);
                }
                continue;
            }

            final WikiNode coordsAttr = box.findChildNamed(ATTR_COORDINATES);
            if (coordsAttr != null) {
                try {
                    return parseCoordinatesAttribute(coordsAttr);
                } catch (Exception ignored) { /* try next thing */ }
            }

            final WikiNode latitude = box.findChildNamed(ATTR_LATITUDE);
            final WikiNode longitude = box.findChildNamed(ATTR_LONGITUDE);
            if (latitude != null && longitude != null) {
                try {
                    return new LatLon(Double.parseDouble(latitude.findAllChildText()), Double.parseDouble(longitude.findAllChildText()));
                } catch (Exception ignored) { /* try next thing */ }
            }

            final WikiNode latd = box.findChildNamed(ATTR_LATD);
            final WikiNode longd = box.findChildNamed(ATTR_LONGD);
            if (latd != null && longd != null) {
                try {
                    return parseLatdLongd(box);
                } catch (Exception ignored) { /* try next thing */ }
            }

            final WikiNode lat_d = box.findChildNamed(ATTR_LAT_D);
            final WikiNode long_d = box.findChildNamed(ATTR_LONG_D);
            if (lat_d != null && long_d != null) {
                try {
                    return parseLat_dLong_d(box);
                } catch (Exception ignored) { /* try next thing */ }
            }

            final WikiNode latdeg = box.findChildNamed(ATTR_LAT_DEG);
            final WikiNode longdeg = box.findChildNamed(ATTR_LON_DEG);
            if (latdeg != null && longdeg != null) {
                try {
                    return parseLatdegLongdeg(box);
                } catch (Exception ignored) { /* try next thing */ }
            }
        }
        return null;
    }

    private LatLon parseCoordinatesAttribute(WikiNode coordsAttr) throws Exception {
        final WikiNode coordsValue = coordsAttr.getChildren().get(0);
        return parseCoordinatesBox(coordsValue);
    }

    private LatLon parseCoordinatesBox(WikiNode coordsValue) {
        if (coordsValue.getType().isInfobox() && coordsValue.getName().equalsIgnoreCase(BOXNAME_COORD)) {
            final List<WikiNode> coordNumbers = coordsValue.getChildren();
            return fromCoordsValue(coordNumbers);
        }
        return die("parseCoordinatesBox: unparseable ("+coordsValue+")");
    }

    private LatLon fromCoordsValue(List<WikiNode> coordNumbers) {
        final Cardinal latCardinal;
        final Double latMin, latSec, lonMin, lonSec;
        final Cardinal lonCardinal;
        int lonStartIndex;
        String val1, val2, val3;

        val1 = coordNumbers.size() > 0 ? coordNumbers.get(0).findAllText() : null;
        val2 = coordNumbers.size() > 1 ? coordNumbers.get(1).findAllText() : null;
        val3 = coordNumbers.size() > 2 ? coordNumbers.get(2).findAllText() : null;

        if (val1 == null || val2 == null) die("Invalid coordinates (a): "+coordNumbers);

        double latDeg = Double.valueOf(val1);
        if (val1.contains(".") && val2.contains(".")) {
            return new LatLon(latDeg, Double.valueOf(val2));
        }

        if (val3 == null) die("Invalid coordinates (b): "+coordNumbers);

        if (val1.contains(".") && Cardinal.isCardinal(val2) && val3.contains(".")) {
            Cardinal latDir = Cardinal.create(val2);
            Cardinal lonDir = Cardinal.create(coordNumbers.get(3).findAllText());
            if (latDir != null && lonDir != null) {
                latDeg *= (double) latDir.getDirection();
                double lonDeg = ((double) lonDir.getDirection()) * Double.valueOf(val3);
                return new LatLon(latDeg, lonDeg);
            }
        }

        if (Cardinal.isCardinal(val2)) {
            latCardinal = Cardinal.create(val2);
            latMin = latSec = null;
            lonStartIndex = 2;

        } else {
            latMin = Double.parseDouble(val2);
            if (Cardinal.isCardinal(val3)) {
                latCardinal = Cardinal.create(val3);
                latSec = null;
                lonStartIndex = 3;
            } else {
                latSec = Double.parseDouble(val3);
                latCardinal = Cardinal.create(coordNumbers.get(3).findAllText());
                lonStartIndex = 4;
            }
        }

        final double lonDeg = Float.valueOf(coordNumbers.get(lonStartIndex).findAllText());
        val2 = coordNumbers.get(lonStartIndex+1).findAllText();
        if (Cardinal.isCardinal(val2)) {
            lonCardinal = Cardinal.create(val2);
            lonMin = lonSec = null;

        } else {
            lonMin = Double.parseDouble(val2);
            val3 = coordNumbers.get(lonStartIndex+2).findAllText();
            if (Cardinal.isCardinal(val3)) {
                lonCardinal = Cardinal.create(val3);
                lonSec = null;
            } else {
                lonSec = Double.parseDouble(val3);
                lonCardinal = Cardinal.create(coordNumbers.get(lonStartIndex+3).findAllText());
            }
        }

        return new LatLon(latDeg, latMin, latSec, latCardinal, lonDeg, lonMin, lonSec, lonCardinal);
    }

    @SuppressWarnings("Duplicates")
    private LatLon parseLatdLongd(WikiNode box) {
        final WikiNode latd = box.findChildNamed(ATTR_LATD);
        final WikiNode latm = box.findChildNamed(ATTR_LATM);
        final WikiNode lats = box.findChildNamed(ATTR_LATS);
        final WikiNode latns = box.findChildNamed(ATTR_LATNS);

        final WikiNode longd = box.findChildNamed(ATTR_LONGD);
        final WikiNode longm = box.findChildNamed(ATTR_LONGM);
        final WikiNode longs = box.findChildNamed(ATTR_LONGS);
        final WikiNode longew = box.findChildNamed(ATTR_LONGEW);

        double latDeg = parseCoordinate(latd, latm, lats, latns);
        double longDeg = parseCoordinate(longd, longm, longs, longew);

        return new LatLon(latDeg, longDeg);
    }

    @SuppressWarnings("Duplicates")
    private LatLon parseLat_dLong_d(WikiNode box) {
        final WikiNode latd = box.findChildNamed(ATTR_LAT_D);
        final WikiNode latm = box.findChildNamed(ATTR_LAT_M);
        final WikiNode lats = box.findChildNamed(ATTR_LAT_S);
        final WikiNode latns = box.findChildNamed(ATTR_LAT_NS);

        final WikiNode longd = box.findChildNamed(ATTR_LONG_D);
        final WikiNode longm = box.findChildNamed(ATTR_LONG_M);
        final WikiNode longs = box.findChildNamed(ATTR_LONG_S);
        final WikiNode longew = box.findChildNamed(ATTR_LONG_EW);

        double latDeg = parseCoordinate(latd, latm, lats, latns);
        double longDeg = parseCoordinate(longd, longm, longs, longew);

        return new LatLon(latDeg, longDeg);
    }

    @SuppressWarnings("Duplicates")
    private LatLon parseLatdegLongdeg(WikiNode box) {
        final WikiNode latd = box.findChildNamed(ATTR_LAT_DEG);
        final WikiNode latm = box.findChildNamed(ATTR_LAT_MIN);
        final WikiNode lats = box.findChildNamed(ATTR_LAT_SEC);
        final WikiNode latns = box.findChildNamed(ATTR_LAT_DIR);

        final WikiNode longd = box.findChildNamed(ATTR_LON_DEG);
        final WikiNode longm = box.findChildNamed(ATTR_LON_MIN);
        final WikiNode longs = box.findChildNamed(ATTR_LON_SEC);
        final WikiNode longew = box.findChildNamed(ATTR_LON_DIR);

        double latDeg = parseCoordinate(latd, latm, lats, latns);
        double longDeg = parseCoordinate(longd, longm, longs, longew);

        return new LatLon(latDeg, longDeg);
    }

    private double parseCoordinate(WikiNode deg, WikiNode min, WikiNode sec, WikiNode card) {
        double latDeg = Double.parseDouble(deg.findAllChildText());
        if (min != null) {
            String val = min.findAllChildText();
            if (!empty(val)) latDeg += Double.parseDouble(val)/60.0;
        }
        if (sec != null) {
            String val = sec.findAllChildText();
            if (!empty(val)) latDeg += Double.parseDouble(val)/3600.0;
        }
        if (card != null) latDeg *= (double) Cardinal.create(card.findAllChildText()).getDirection();
        return latDeg;
    }

}
