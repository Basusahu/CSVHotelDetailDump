
// CODE NO .3 
package com.example.CSVBuilder;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;

public class App {


	public static void main(String[] args) {
	    try {
	        String username = "TBOStaticAPITest";
	        String password = "Tbo@11530818";

	        // Combine username and password with a colon
	        String credentials = username + ":" + password;

	        // Encode the credentials using Base64
	        String encodedCredentials = Base64.getEncoder().encodeToString(credentials.getBytes());

	        // Create the Authorization header
	        String authHeader = "Basic " + encodedCredentials;
	        System.out.println(authHeader);

	        // Step 1: Call the CityList API and fetch the city data
	        String cityListApiUrl = "http://api.tbotechnology.in/TBOHolidays_HotelAPI/CityList";
	        String cityListResponse = fetchCityList(cityListApiUrl, authHeader);

	        // Step 2: Parse the CityList response and get the city details
	        JsonObject cityListJson = new Gson().fromJson(cityListResponse, JsonObject.class);
	        JsonArray cityList = cityListJson.getAsJsonArray("CityList");

	        // Prepare file writer for the common CSV
	        try (FileWriter commonWriter = new FileWriter("common_hotels.csv", true)) {
	            // Step 3: For each city in the list, call the hotel details API and generate a CSV file
	            for (int i = 0; i < cityList.size(); i++) {
	                JsonObject city = cityList.get(i).getAsJsonObject();
	                String cityCode = city.get("Code").getAsString();
	                String cityName = city.get("Name").getAsString();
	                System.out.println("Processing City: " + cityName + " (CityCode: " + cityCode + ")");

	                // Call the TBOHotelCodeList API to fetch hotel details
	                String hotelDetailsApiUrl = "http://api.tbotechnology.in/TBOHolidays_HotelAPI/TBOHotelCodeList";
	                String hotelDetailsResponse = fetchHotelDataWithRetry(hotelDetailsApiUrl, cityCode, authHeader, true);

	                // Parse the hotel details response
	                JsonObject hotelDetailsJson = new Gson().fromJson(hotelDetailsResponse, JsonObject.class);

	                JsonArray hotels;
	                if (hotelDetailsJson.has("Hotels")) {
	                    hotels = hotelDetailsJson.getAsJsonArray("Hotels");
	                } else {
	                    // If the "Hotels" key is not present, set hotels to an empty array or log the absence
	                    hotels = new JsonArray(); // Initializing to empty array
	                    System.out.println("No 'Hotels' key found in the hotel details JSON.");
	                }

	                if (hotelDetailsJson.getAsJsonObject("Status").get("Code").getAsInt() == 200 && hotels.size() > 0) {
	                    // Save hotel details to the individual city CSV
	                    saveHotelsToCsv(hotelDetailsResponse, cityName);

	                    // Save hotel details to the common CSV
	                    saveToCommonCsv(hotelDetailsResponse, commonWriter);
	                } else {
	                    System.out.println("No hotels found for city: " + cityName + ", retrying with IsDetailedResponse=false");

	                    // Retry with IsDetailedResponse=false
	                    hotelDetailsResponse = fetchHotelDataWithRetry(hotelDetailsApiUrl, cityCode, authHeader, false);

	                    // Parse the second response
	                    hotelDetailsJson = new Gson().fromJson(hotelDetailsResponse, JsonObject.class);
	                    hotels = hotelDetailsJson.has("Hotels") ? hotelDetailsJson.getAsJsonArray("Hotels") : new JsonArray();

	                    if (hotels.size() == 0) {
	                        System.out.println("No hotels found for city after retrying with IsDetailedResponse=false. Writing default entry.");
	                    }

	                    // Save hotel details to the individual city CSV (if any hotels are found in the second call)
	                    saveHotelsToCsv(hotelDetailsResponse, cityName);

	                    // Save hotel details to the common CSV
	                    saveToCommonCsv(hotelDetailsResponse, commonWriter);
	                }
	            }
	        }

	        System.out.println("Common CSV file created successfully: common_hotels.csv");

	    } catch (Exception e) {
	        System.out.println("Error occurred while processing the data: " + e.getMessage());
	        e.printStackTrace();
	    }
	}

// Fetch the CityList from the API
private static String fetchCityList(String apiUrl, String authHeader) throws IOException {
	URL url = new URL(apiUrl);
	HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	connection.setRequestMethod("POST");
	connection.setRequestProperty("Content-Type", "application/json");
	connection.setRequestProperty("Authorization", authHeader);
	connection.setDoOutput(true);

	String requestBody = "{\"CountryCode\":\"IN\"}";
	try (OutputStream os = connection.getOutputStream()) {
		os.write(requestBody.getBytes());
		os.flush();
	}

	int responseCode = connection.getResponseCode();
	if (responseCode == 200) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		return response.toString();
	} else {
		throw new IOException("Failed to fetch city list. HTTP response code: " + responseCode);
	}
}

// Fetch hotel data using CityCode from the TBOHotelCodeList API with retry
private static String fetchHotelDataWithRetry(String apiUrl, String cityCode, String authHeader,
		boolean isDetailResponse) {
	String hotelDetailsResponse = null;

	try {
		hotelDetailsResponse = fetchHotelData(apiUrl, cityCode, authHeader, isDetailResponse);
		return hotelDetailsResponse; // If successful, return the response
	} catch (IOException e) {
		e.getMessage();
	}
	return hotelDetailsResponse;
}

// Fetch hotel data from the TBOHotelCodeList API
private static String fetchHotelData(String apiUrl, String cityCode, String authHeader, boolean isDetailResponse)
		throws IOException {
	URL url = new URL(apiUrl);
	HttpURLConnection connection = (HttpURLConnection) url.openConnection();
	connection.setRequestMethod("POST");
	connection.setRequestProperty("Content-Type", "application/json");
	connection.setRequestProperty("Authorization", authHeader);
	connection.setDoOutput(true);

	String requestBody = String.format("{\"CityCode\": \"%s\", \"IsDetailedResponse\": \"%s\"}", cityCode,
			isDetailResponse);
	try (OutputStream os = connection.getOutputStream()) {
		os.write(requestBody.getBytes());
		os.flush();
	}

	int responseCode = connection.getResponseCode();
	if (responseCode == 200) {
		BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		StringBuilder response = new StringBuilder();
		String line;
		while ((line = reader.readLine()) != null) {
			response.append(line);
		}
		return response.toString();
	} else {
		throw new IOException("Failed to fetch hotel data. HTTP response code: " + responseCode);
	}
}

// Save hotel data to the common CSV file
private static void saveToCommonCsv(String hotelDetailsResponse, FileWriter commonWriter) throws IOException {
	Gson gson = new Gson();
	JsonObject hotelDetailsJson = gson.fromJson(hotelDetailsResponse, JsonObject.class);

	if (hotelDetailsJson.has("Hotels") && hotelDetailsJson.get("Hotels").isJsonArray()) {
		JsonArray hotels = hotelDetailsJson.getAsJsonArray("Hotels");

		commonWriter.append(
				"handler_type||property_id||country||address||category||chain_name||city||country_name||created||google_address||google_latitude||google_longitude||hotel_chain||hotel_name||latitude||longitude||phone_number||postal_code||state||updated||cityCode||description||amenities||checkin||checkout||review_rating||review_count||thumbnail||gallop_id\n");

		// Iterate over all hotels and write to CSV
		for (int i = 0; i < hotels.size(); i++) {
			JsonObject hotel = hotels.get(i).getAsJsonObject();
			StringBuffer res = hotelData( hotel);

			// Write hotel data to the common CSV
			commonWriter.append(res.toString()).append("\n");
		}
	}
}

// Save hotel data to the individual city's CSV
private static void saveHotelsToCsv(String hotelDetailsResponse, String cityName) {
	try {
		Gson gson = new Gson();
		JsonObject hotelDetailsJson = gson.fromJson(hotelDetailsResponse, JsonObject.class);

		if (hotelDetailsJson.has("Hotels") && hotelDetailsJson.get("Hotels").isJsonArray()) {
			JsonArray hotels = hotelDetailsJson.getAsJsonArray("Hotels");

			FileWriter cityWriter = new FileWriter(cityName + "_hotels.csv", true);

			// Write the header only once
			cityWriter.append(
					"handler_type||property_id||country||address||category||chain_name||city||country_name||created||google_address||google_latitude||google_longitude||hotel_chain||hotel_name||latitude||longitude||phone_number||postal_code||state||updated||cityCode||description||amenities||checkin||checkout||review_rating||review_count||thumbnail||gallop_id\n");

			// Iterate over all hotels and save them to the individual city CSV
			for (int i = 0; i < hotels.size(); i++) {
				JsonObject hotel = hotels.get(i).getAsJsonObject();
				StringBuffer res = hotelData( hotel);
				cityWriter.append(res.toString()).append("\n");
			}
			cityWriter.close();
		}
	} catch (IOException e) {
		e.printStackTrace();
	}
}


public static StringBuffer hotelData( JsonObject hotel) {
	StringBuffer entry = new StringBuffer();


	Object handlerType = null;
	entry.append( handlerType != null ? handlerType : "TRAVELBOUTIQUE").append("||");
	entry.append(hotel.has("HotelCode") ? hotel.get("HotelCode").getAsString() : "").append("||");
	entry.append(hotel.has("CountryCode") ? hotel.get("CountryCode").getAsString().toUpperCase() : "").append("||");
	entry.append(hotel.has("Address") ? hotel.get("Address").getAsString() : "").append("||");
	entry.append(hotel.has("Category") ? hotel.get("Category").getAsString() : "").append("||");
	entry.append(hotel.has("ChainName") ? hotel.get("ChainName").getAsString() : "").append("||");
	entry.append(hotel.has("CityName") ? hotel.get("CityName").getAsString() : "").append("||");
	entry.append(hotel.has("CountryName") ? hotel.get("CountryName").getAsString() : "").append("||");
	entry.append(hotel.has("created") ? hotel.get("created").getAsString() : "").append("||");
	entry.append(hotel.has("googleAddress") ? hotel.get("googleAddress").getAsString() : "").append("||");
	entry.append(hotel.has("google_lattitude") ? hotel.get("google_lattitude").getAsString() : "").append("||");
	entry.append(hotel.has("google_longitude") ? hotel.get("google_longitude").getAsString() : "").append("||");
	entry.append(hotel.has("hotelChain") ? hotel.get("hotelChain").getAsString() : "").append("||");
	entry.append(hotel.has("HotelName") ? hotel.get("HotelName").getAsString() : "").append("||");

	String map = hotel.has("Map") ? hotel.get("Map").getAsString() : null;
	String latitude = null, longitude = null;
	if (map != null) {
		String[] latLong = map.split("\\|");
		if (latLong.length == 2) {
			latitude = latLong[0];
			longitude = latLong[1];
		}
	} else {
		latitude = hotel.has("Latitude") ? hotel.get("Latitude").getAsString() : null;
		longitude = hotel.has("Longitude") ? hotel.get("Longitude").getAsString() : null;
	}
	entry.append(latitude != null ? latitude : "").append("||");
	entry.append(longitude != null ? longitude : "").append("||");

	entry.append( hotel.has("PhoneNumber") ? (hotel.get("PhoneNumber").getAsString() != null  ? hotel.get("PhoneNumber").getAsString().split("\\|")[0].trim()  : "")   : "").append("||");
	entry.append(hotel.has("PinCode") ? hotel.get("PinCode").getAsString() : "").append("||");
	entry.append(hotel.has("State") ? hotel.get("State").getAsString() : "").append("||");
	entry.append(hotel.has("updated") ? hotel.get("updated").getAsString() : "").append("||");
	entry.append(hotel.has("CityCode") ? hotel.get("CityCode").getAsString() : "").append("||");
	Object description = null;
	entry.append(description = null != null ? description : "").append("||");
	entry.append(hotel.has("Amenities") ? hotel.get("Amenities").getAsString() : "").append("||");
	entry.append(hotel.has("CheckIn") ? hotel.get("CheckIn").getAsString() : "").append("||");
	entry.append(hotel.has("CheckOut") ? hotel.get("CheckOut").getAsString() : "").append("||");

	String reviewRating = null;
	if (hotel.has("HotelRating")) {
		switch (hotel.get("HotelRating").getAsString()) {
		case "OneStar": reviewRating = "1"; break;
		case "TwoStar": reviewRating = "2"; break;
		case "ThreeStar": reviewRating = "3"; break;
		case "FourStar": reviewRating = "4"; break;
		case "FiveStar": reviewRating = "5"; break;
		default: reviewRating = "5"; break;
		}
	}
	entry.append(reviewRating != null ? reviewRating : "").append("||");

	entry.append(hotel.has("ReviewCount") ? hotel.get("ReviewCount").getAsString() : "").append("||");
	entry.append(hotel.has("Thumbnail") ? hotel.get("Thumbnail").getAsString() : "").append("||");
	entry.append(hotel.has("GallopId") ? hotel.get("GallopId").getAsString() : "");

	return entry;
}
}
