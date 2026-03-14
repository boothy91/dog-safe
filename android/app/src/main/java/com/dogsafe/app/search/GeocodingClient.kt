package com.dogsafe.app.search

import com.google.gson.annotations.SerializedName
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

data class SearchResult(
    @SerializedName("display_name") val displayName: String,
    @SerializedName("lat") val lat: Double,
    @SerializedName("lon") val lon: Double,
    @SerializedName("type") val type: String = "",
    @SerializedName("class") val clazz: String = ""
) {
    // Shorten display name to first two parts for list display
    val shortName: String get() {
        val parts = displayName.split(",")
        return if (parts.size >= 2) "${parts[0].trim()}, ${parts[1].trim()}"
        else parts[0].trim()
    }
}

interface NominatimApi {
    @GET("search")
    suspend fun search(
        @Query("q")               query: String,
        @Query("format")          format: String = "json",
        @Query("countrycodes")    countrycodes: String = "gb",
        @Query("limit")           limit: Int = 8,
        @Query("addressdetails")  addressdetails: Int = 0
    ): List<SearchResult>
}

object GeocodingClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            // Nominatim requires a User-Agent
            val request = chain.request().newBuilder()
                .header("User-Agent", "DogSafe/1.0 (com.dogsafe.app)")
                .build()
            chain.proceed(request)
        }
        .build()

    private val api: NominatimApi = Retrofit.Builder()
        .baseUrl("https://nominatim.openstreetmap.org/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NominatimApi::class.java)

    suspend fun search(query: String): List<SearchResult> {
        return try {
            api.search(query).map { result ->
                // Shorten display name for the list
                result.copy(displayName = result.shortName)
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
