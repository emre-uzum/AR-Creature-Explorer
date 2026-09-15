/*
 * OsmPoiRepository is responsible for retrieving nearby Points of Interest (POIs)
 * using the OpenStreetMap Overpass API.
 *
 * It integrates external geospatial data into the game to enhance exploration,
 * allowing real-world locations to act as interactive gameplay elements.
 *
 * The repository:
 * - constructs Overpass queries dynamically based on player location
 * - performs asynchronous network requests
 * - parses JSON responses into domain objects (PointOfInterest)
 * - maps POIs to gameplay rewards (token system)
 *
 * References:
 * Overpass API. https://overpass-api.de
 * Riccardo Scotti (2025). A Practical Guide to Overpass API Query Language.
 * https://riccardoscott1.github.io/articles/Geospatial-Series/OpenStreetMap-Data
 */

package com.emreuzum.argame.game;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class OsmPoiRepository {

    /*
     * Callback interface for asynchronous POI retrieval.
     * Ensures network operations do not block the main UI thread.
     */
    public interface PoiListCallback{
        void onSuccess(@NonNull List<PointOfInterest> pois);
        void onError(@NonNull Exception exception);
    }

    /*
     * Overpass API endpoint for executing queries.
     */
    private static final String OVERPASS_URL = "https://overpass-api.de/api/interpreter";

    /*
     * Defines the search radius around the player (in metres).
     * A moderate radius balances data richness with performance.
     */
    private static final int SEARCH_RADIUS_METRES = 1500;

    /*
     * Single-thread executor used to perform network operations off the UI thread.
     * Prevents blocking and ensures responsiveness of the application.
     */
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public void fetchNearbyPois(double latitude, double longitude) {
        // Unused overload – retained for potential future extension
    }

    /*
     * Fetches nearby POIs asynchronously.
     *
     * Flow:
     * 1. Build Overpass query
     * 2. Execute HTTP request
     * 3. Parse JSON response into POI objects
     * 4. Return results via callback
     */
    public void fetchNearbyPois(double latitude, double longitude, PoiListCallback callback) {
        executorService.execute(() -> {
            try {
                String query = buildOverpassQuery(latitude, longitude);
                String response = postOverpassQuery(query);
                List<PointOfInterest> pois = parsePois(response);
                callback.onSuccess(pois);
            } catch (Exception exception) {
                callback.onError(exception);
            }
        });
    }

    /*
     * Builds an Overpass QL query to retrieve nearby POIs.
     *
     * Filters are applied to include meaningful gameplay locations
     * such as cultural, historic, and leisure sites.
     */
    private String buildOverpassQuery(double latitude, double longitude) {
        return "[out:json][timeout:20];"
                + "("
                + "nwr(around:" + SEARCH_RADIUS_METRES + "," + latitude + "," + longitude + ")[amenity~\"library|fountain|community_centre|arts_centre|place_of_worship\"];"
                + "nwr(around:" + SEARCH_RADIUS_METRES + "," + latitude + "," + longitude + ")[tourism~\"museum|artwork|attraction|viewpoint\"];"
                + "nwr(around:" + SEARCH_RADIUS_METRES + "," + latitude + "," + longitude + ")[historic~\"memorial|monument\"];"
                + "nwr(around:" + SEARCH_RADIUS_METRES + "," + latitude + "," + longitude + ")[leisure~\"park|playground|sports_centre\"];"
                + ");"
                + "out center;";
    }

    /*
     * Executes the Overpass query via HTTP POST.
     *
     * Handles:
     * - connection setup
     * - request encoding
     * - response reading
     * - error handling based on HTTP status codes
     */
    private String postOverpassQuery(String query) throws Exception {
        URL url = new URL(OVERPASS_URL);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        connection.setConnectTimeout(15000);
        connection.setReadTimeout(20000);

        String body = "data=" + java.net.URLEncoder.encode(query, "UTF-8");

        try (OutputStream os = connection.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int statusCode = connection.getResponseCode();
        InputStream stream = (statusCode >= 200 && statusCode < 300)
                ? connection.getInputStream()
                : connection.getErrorStream();

        if (stream == null) {
            throw new IllegalStateException("No response from Overpass API");
        }

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            StringBuilder builder = new StringBuilder();
            String line;

            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }

            if (statusCode < 200 || statusCode >= 300) {
                throw new IllegalStateException("Overpass API error: " + builder);
            }

            return builder.toString();
        } finally {
            connection.disconnect();
        }
    }

    /*
     * Parses Overpass JSON response into PointOfInterest objects.
     *
     * Handles both node and way/relation formats by checking:
     * - direct lat/lon
     * - or 'center' object
     */
    private List<PointOfInterest> parsePois(String response) throws Exception {
        List<PointOfInterest> pois = new ArrayList<>();

        JSONObject root = new JSONObject(response);
        JSONArray elements = root.getJSONArray("elements");

        for (int i = 0; i < elements.length(); i++) {
            JSONObject element = elements.getJSONObject(i);
            JSONObject tags = element.optJSONObject("tags");

            if (tags == null) {
                continue;
            }

            double lat;
            double lon;

            // Handle different geometry types from OSM
            if (element.has("lat") && element.has("lon")) {
                lat = element.getDouble("lat");
                lon = element.getDouble("lon");
            } else if (element.has("center")) {
                JSONObject center = element.getJSONObject("center");
                lat = center.getDouble("lat");
                lon = center.getDouble("lon");
            } else {
                continue;
            }

            String name = tags.optString("name", "Point of Interest");
            String category = extractCategory(tags);
            int tokenReward = tokenRewardForCategory(category);

            /*
             * Unique POI identifier based on OSM type and ID.
             * Ensures consistent identification across sessions.
             */
            String poiId = element.getString("type") + "_" + element.getLong("id");

            pois.add(new PointOfInterest(poiId, name, lat, lon, tokenReward, category));
        }

        return pois;
    }

    /*
     * Extracts a meaningful category from OSM tags.
     * Priority order ensures consistent classification.
     */
    private String extractCategory(JSONObject tags) {
        if (tags.has("amenity")){
            return tags.optString("amenity");
        }
        if (tags.has("tourism")){
            return tags.optString("tourism");
        }
        if (tags.has("historic")){
            return tags.optString("historic");
        }
        if (tags.has("leisure")){
            return tags.optString("leisure");
        }
        return "poi";
    }

    /*
     * Assigns gameplay rewards based on POI category.
     *
     * Higher-value or culturally significant locations
     * provide greater rewards to encourage exploration.
     */
    private int tokenRewardForCategory(String category) {
        switch (category) {
            case "museum":
            case "attraction":
            case "monument":
                return 3;
            case "library":
            case "artwork":
            case "memorial":
            case "viewpoint":
                return 2;
            default:
                return 1;
        }
    }
}