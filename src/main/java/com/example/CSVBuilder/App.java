
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
			Gson gson = new Gson();
			JsonObject cityListJson = gson.fromJson(cityListResponse, JsonObject.class);
			JsonArray cityList = cityListJson.getAsJsonArray("CityList");

			// Prepare file writers for the individual city CSV and the common CSV
			FileWriter commonWriter = new FileWriter("common_hotels.csv", true); // Open file in append mode
			boolean isHeaderWrittenForCommonCsv = false;

			// Step 3: For each city in the list, call the hotel details API and generate a
			// CSV file
			for (int i = 0; i < cityList.size(); i++) {
				JsonObject city = cityList.get(i).getAsJsonObject();
				String cityCode = city.get("Code").getAsString();
				String cityName = city.get("Name").getAsString();
				System.out.println("Processing City: " + cityName + " (CityCode: " + cityCode + ")");

				// Call the TBOHotelCodeList API to fetch hotel details
				String hotelDetailsApiUrl = "http://api.tbotechnology.in/TBOHolidays_HotelAPI/TBOHotelCodeList";
				String hotelDetailsResponse = fetchHotelDataWithRetry(hotelDetailsApiUrl, cityCode, authHeader, true);
				String hotelDetailsResponse1 = fetchHotelDataWithRetry(hotelDetailsApiUrl, cityCode, authHeader, false);

				if (hotelDetailsResponse != null && !hotelDetailsResponse.contains("\"Hotels\": []")) {
					// Save hotel details to the individual city CSV
					saveHotelsToCsv(hotelDetailsResponse, cityName);

					// Save hotel details to the common CSV
					saveToCommonCsv(hotelDetailsResponse, cityName, commonWriter, isHeaderWrittenForCommonCsv);
					isHeaderWrittenForCommonCsv = true; // After the first city, the header will be written
				} else {
					System.out.println(
							"No hotels found for city: " + cityName + ", retrying with IsDetailedResponse=false");

					// Retry with IsDetailedResponse=false
					hotelDetailsResponse = fetchHotelDataWithRetry(hotelDetailsApiUrl, cityCode, authHeader, false);

					if (hotelDetailsResponse != null && hotelDetailsResponse.contains("\"Hotels\": []")) {
						System.out.println(
								"No hotels found for city after retrying with IsDetailedResponse=false. Writing default entry.");
					}

					// Save hotel details to the individual city CSV (if any hotels are found in the
					// second call)
					saveHotelsToCsv(hotelDetailsResponse, cityName);

					// Save hotel details to the common CSV
					saveToCommonCsv(hotelDetailsResponse, cityName, commonWriter, isHeaderWrittenForCommonCsv);
					isHeaderWrittenForCommonCsv = true;
				}
			}

			// Close the writer for the common CSV
			commonWriter.close();
			System.out.println("Common CSV file created successfully: common_hotels.csv");

		} catch (Exception e) {
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
	// mechanism
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
	private static boolean saveToCommonCsv(String hotelDetailsResponse, String cityName, FileWriter commonWriter, boolean isHeaderWritten) throws IOException {
	    Gson gson = new Gson();
	    JsonObject hotelDetailsJson = gson.fromJson(hotelDetailsResponse, JsonObject.class);

	    if (hotelDetailsJson.has("Hotels") && hotelDetailsJson.get("Hotels").isJsonArray()) {
	        JsonArray hotels = hotelDetailsJson.getAsJsonArray("Hotels");

	        // Write header only once
	        if (!isHeaderWritten) {
	            commonWriter.append("handler_type||property_id||country||address||category||chain_name||city||country_name||created||google_address||google_latitude||google_longitude||hotel_chain||hotel_name||latitude||longitude||phone_number||postal_code||state||updated||cityCode||description||amenities||checkin||checkout||review_rating||review_count||thumbnail||gallop_id\n");
	            isHeaderWritten = true;
	        }


				// Write hotel data to the common CSV
				for (int i = 0; i < hotels.size(); i++) {
					JsonObject hotel = hotels.get(i).getAsJsonObject();

					StringBuffer entry = new StringBuffer();
					String handlerType = "TRAVELBOUTIQUE"; // Set appropriate value if available
					appendColumnValue(entry, handlerType);
					String propertyId = null;
					if (hotel.has("HotelCode")) {
						propertyId = hotel.get("HotelCode").getAsString();
					}
					appendColumnValue(entry, propertyId);

					String country = null;
					if (hotel.has("CountryCode")) {
						country = hotel.get("CountryCode").getAsString().toUpperCase();
					}
					appendColumnValue(entry, country);

					String address = null;
					if (hotel.has("Address")) {
						address = hotel.get("Address").getAsString();
					}
					appendColumnValue(entry, address);

					String category = null;
					if (hotel.has("Category")) {
						category = hotel.get("Category").getAsString();
					}

					appendColumnValue(entry, category);

					String chainName = null;
					if (hotel.has("ChainName")) {
						chainName = hotel.get("ChainName").getAsString();
					}
					appendColumnValue(entry, chainName);

					String city = cityName;
					// Assuming cityName is predefined elsewhere
					appendColumnValue(entry, city);

					String countryName = null;
					if (hotel.has("CountryName")) {
						countryName = hotel.get("CountryName").getAsString();
					}
					appendColumnValue(entry, countryName);

					String created = null;
					if (hotel.has("created")) {
						created = hotel.get("created").getAsString();
					}
					appendColumnValue(entry, created);

					String googleAddress = null;
					if (hotel.has("googleAddress")) {
						googleAddress = hotel.get("googleAddress").getAsString();
					}
					appendColumnValue(entry, googleAddress);

					String google_lattitude = null;
					if (hotel.has("google_lattitude")) {
						google_lattitude = hotel.get("google_lattitude").getAsString();
					}
					appendColumnValue(entry, google_lattitude);

					String google_longitude = null;
					if (hotel.has("google_longitude")) {
						google_longitude = hotel.get("google_longitude").getAsString();
					}
					appendColumnValue(entry, google_longitude);
					String hotelChain = null;
					if (hotel.has("hotelChain")) {
						hotelChain = hotel.get("hotelChain").getAsString();
					}
					appendColumnValue(entry, hotelChain);

					String hotelName = null;
					if (hotel.has("HotelName")) {
						hotelName = hotel.get("HotelName").getAsString();
					}
					appendColumnValue(entry, hotelName);

					String map = null;
					if (hotel.has("Map")) {
						map = hotel.get("Map").getAsString();
					}

					String latitude = null;
					String longitude = null;
					if (map != null) {
						String[] latLong = map.split("\\|");
						if (latLong.length == 2) {
							latitude = latLong[0];
							longitude = latLong[1];
						}
					}
					appendColumnValue(entry, latitude);
					appendColumnValue(entry, longitude);

					String phoneNumber = null;
					if (hotel.has("PhoneNumber")) {
						phoneNumber = hotel.get("PhoneNumber").getAsString();
						phoneNumber = (phoneNumber != null) ? phoneNumber.split("\\|")[0].trim() : null;
					}
					appendColumnValue(entry, phoneNumber);

					String postalCode = null;
					if (hotel.has("PinCode")) {
						postalCode = hotel.get("PinCode").getAsString();
					}
					appendColumnValue(entry, postalCode);

					String state = null;
					if (hotel.has("StateName")) {
						state = hotel.get("StateName").getAsString();
					}
					appendColumnValue(entry, state);

					String updated = null;
					if (hotel.has("updated")) {
						updated = hotel.get("updated").getAsString();
					}
					appendColumnValue(entry, updated);

					String cityCode = null;
					if (hotel.has("cityCode")) {
						cityCode = hotel.get("cityCode").getAsString();
					}
					appendColumnValue(entry, cityCode);

					String description = null; // Placeholder for description if you need to clean HTML tags
					// String description = hotel.has("Description") ?
					// cleanHtmlTags(hotel.get("Description").getAsString()) : null;

					appendColumnValue(entry, description);

					String amenities = null;
					if (hotel.has("Amenities")) {
						amenities = hotel.get("Amenities").getAsString();
					}
					appendColumnValue(entry, amenities);

					String checkin = null;
					if (hotel.has("CheckinTime")) {
						checkin = hotel.get("CheckinTime").getAsString();
					}
					appendColumnValue(entry, checkin);

					String checkout = null;
					if (hotel.has("CheckoutTime")) {
						checkout = hotel.get("CheckoutTime").getAsString();
					}
					appendColumnValue(entry, checkout);

					String reviewRating = null;
					if (hotel.has("HotelRating")) {
						String hotelRating = hotel.get("HotelRating").getAsString();
						switch (hotelRating) {
						case "OneStar":
							reviewRating = "1";
							break;
						case "TwoStar":
							reviewRating = "2";
							break;
						case "ThreeStar":
							reviewRating = "3";
							break;
						case "FourStar":
							reviewRating = "4";
							break;
						case "FiveStar":
							reviewRating = "5";
							break;
						default:
							reviewRating = "5"; // Default to 5 if rating is unknown
						}
					}
					appendColumnValue(entry, reviewRating);

					String reviewCount = null;
					if (hotel.has("ReviewCount")) {
						reviewCount = hotel.get("ReviewCount").getAsString();
					}
					appendColumnValue(entry, reviewCount);

					String thumbnail = null;
					if (hotel.has("Thumbnail")) {
						thumbnail = hotel.get("Thumbnail").getAsString();
					}
					appendColumnValue(entry, thumbnail);

					String gallopId = null;
					if (hotel.has("GallopId")) {
						gallopId = hotel.get("GallopId").getAsString();
					}
					appendColumnValue(entry, gallopId);

		            commonWriter.append(entry.toString()).append("\n");
		        }
		    }
		    return isHeaderWritten;
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

				// Write hotel data to the individual city's CSV
				for (int i = 0; i < hotels.size(); i++) {
					JsonObject hotel = hotels.get(i).getAsJsonObject();
					//
					StringBuffer entry = new StringBuffer();
					String handlerType = "TRAVELBOUTIQUE"; // Set appropriate value if available
					appendColumnValue(entry, handlerType);
					String propertyId = null;
					if (hotel.has("HotelCode")) {
						propertyId = hotel.get("HotelCode").getAsString();
					}
					appendColumnValue(entry, propertyId);

					String country = null;
					if (hotel.has("CountryCode")) {
						country = hotel.get("CountryCode").getAsString().toUpperCase();
					}
					appendColumnValue(entry, country);

					String address = null;
					if (hotel.has("Address")) {
						address = hotel.get("Address").getAsString();
					}
					appendColumnValue(entry, address);

					String category = null;
					if (hotel.has("Category")) {
						category = hotel.get("Category").getAsString();
					}

					appendColumnValue(entry, category);

					String chainName = null;
					if (hotel.has("ChainName")) {
						chainName = hotel.get("ChainName").getAsString();
					}
					appendColumnValue(entry, chainName);

					String city = cityName;
					// Assuming cityName is predefined elsewhere
					appendColumnValue(entry, city);

					String countryName = null;
					if (hotel.has("CountryName")) {
						countryName = hotel.get("CountryName").getAsString();
					}
					appendColumnValue(entry, countryName);

					String created = null;
					if (hotel.has("created")) {
						created = hotel.get("created").getAsString();
					}
					appendColumnValue(entry, created);

					String googleAddress = null;
					if (hotel.has("googleAddress")) {
						googleAddress = hotel.get("googleAddress").getAsString();
					}
					appendColumnValue(entry, googleAddress);

					String google_lattitude = null;
					if (hotel.has("google_lattitude")) {
						google_lattitude = hotel.get("google_lattitude").getAsString();
					}
					appendColumnValue(entry, google_lattitude);

					String google_longitude = null;
					if (hotel.has("google_longitude")) {
						google_longitude = hotel.get("google_longitude").getAsString();
					}
					appendColumnValue(entry, google_longitude);

					String hotelChain = null;
					if (hotel.has("hotelChain")) {
						hotelChain = hotel.get("hotelChain").getAsString();
					}
					appendColumnValue(entry, hotelChain);

					String hotelName = null;
					if (hotel.has("HotelName")) {
						hotelName = hotel.get("HotelName").getAsString();
					}
					appendColumnValue(entry, hotelName);

					String map = null;
					if (hotel.has("Map")) {
						map = hotel.get("Map").getAsString();
					}

					String latitude = null;
					String longitude = null;
					if (map != null) {
						String[] latLong = map.split("\\|");
						if (latLong.length == 2) {
							latitude = latLong[0];
							longitude = latLong[1];
						}
					}
					appendColumnValue(entry, latitude);
					appendColumnValue(entry, longitude);

					String phoneNumber = null;
					if (hotel.has("PhoneNumber")) {
						phoneNumber = hotel.get("PhoneNumber").getAsString();
						phoneNumber = (phoneNumber != null) ? phoneNumber.split("\\|")[0].trim() : null;
					}
					appendColumnValue(entry, phoneNumber);

					String postalCode = null;
					if (hotel.has("PinCode")) {
						postalCode = hotel.get("PinCode").getAsString();
					}
					appendColumnValue(entry, postalCode);

					String state = null;
					if (hotel.has("StateName")) {
						state = hotel.get("StateName").getAsString();
					}
					appendColumnValue(entry, state);

					String updated = null;
					if (hotel.has("updated")) {
						updated = hotel.get("updated").getAsString();
					}
					appendColumnValue(entry, updated);

					String cityCode = null;
					if (hotel.has("cityCode")) {
						cityCode = hotel.get("cityCode").getAsString();
					}
					appendColumnValue(entry, cityCode);

					String description = null; // Placeholder for description if you need to clean HTML tags
					// String description = hotel.has("Description") ?
					// cleanHtmlTags(hotel.get("Description").getAsString()) : null;

					appendColumnValue(entry, description);

					String amenities = null;
					if (hotel.has("Amenities")) {
						amenities = hotel.get("Amenities").getAsString();
					}
					appendColumnValue(entry, amenities);

					String checkin = null;
					if (hotel.has("CheckinTime")) {
						checkin = hotel.get("CheckinTime").getAsString();
					}
					appendColumnValue(entry, checkin);

					String checkout = null;
					if (hotel.has("CheckoutTime")) {
						checkout = hotel.get("CheckoutTime").getAsString();
					}
					appendColumnValue(entry, checkout);

					String reviewRating = null;
					if (hotel.has("HotelRating")) {
						String hotelRating = hotel.get("HotelRating").getAsString();
						switch (hotelRating) {
						case "OneStar":
							reviewRating = "1";
							break;
						case "TwoStar":
							reviewRating = "2";
							break;
						case "ThreeStar":
							reviewRating = "3";
							break;
						case "FourStar":
							reviewRating = "4";
							break;
						case "FiveStar":
							reviewRating = "5";
							break;
						default:
							reviewRating = "5"; // Default to 5 if rating is unknown
						}
					}
					appendColumnValue(entry, reviewRating);

					String reviewCount = null;
					if (hotel.has("ReviewCount")) {
						reviewCount = hotel.get("ReviewCount").getAsString();
					}
					appendColumnValue(entry, reviewCount);

					String thumbnail = null;
					if (hotel.has("Thumbnail")) {
						thumbnail = hotel.get("Thumbnail").getAsString();
					}
					appendColumnValue(entry, thumbnail);

					String gallopId = null;

					if (hotel.has("GallopId")) {
						gallopId = hotel.get("GallopId").getAsString();
					}
					appendColumnValue(entry, gallopId);

					cityWriter.append(entry.toString() + "\n");

				}
				cityWriter.close();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static void appendColumnValue(StringBuffer entry, String value) {
		if (value != null) {
			entry.append(value);
		}
		entry.append("||");
	}

	// public static String cleanHtmlTags(String input) {
	// if (input == null) {
	// return null;
	// }
	// return input.replaceAll("<.*?>", "");
	// }

}
