package com.dogsafe.app.wales

import com.dogsafe.app.model.Restriction
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query
import java.util.concurrent.TimeUnit

interface NrwWfsApi {
    @GET("wfs")
    suspend fun getAccessLand(
        @Query("service")      service: String = "WFS",
        @Query("version")      version: String = "2.0.0",
        @Query("request")      request: String = "GetFeature",
        @Query("typeName")     typeName: String,
        @Query("outputFormat") outputFormat: String = "application/json",
        @Query("srsName")      srsName: String = "EPSG:4326",
        @Query("bbox")         bbox: String,
        @Query("count")        count: Int = 200
    ): JsonObject
}

object WalesApiClient {

    // Wales bounding box (rough)
    private const val WALES_MIN_LAT = 51.3
    private const val WALES_MAX_LAT = 53.5
    private const val WALES_MIN_LON = -5.4
    private const val WALES_MAX_LON = -2.6

    // NRW access land layers
    private val LAYERS = listOf(
        "inspire-nrw:NRW_OPEN_COUNTRY_2014",
        "inspire-nrw:NRW_COMMON_LAND_2014",
        "inspire-nrw:NRW_PUBLIC_FOREST_2014",
        "inspire-nrw:NRW_OTHER_DEDICATED_LAND"
    )

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "DogSafe/1.0 (com.dogsafe.app)")
                .build()
            chain.proceed(req)
        }
        .build()

    private val api: NrwWfsApi = Retrofit.Builder()
        .baseUrl("https://datamap.gov.wales/geoserver/inspire-nrw/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NrwWfsApi::class.java)

    fun isInWales(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): Boolean {
        return lonEast > WALES_MIN_LON && lonWest < WALES_MAX_LON &&
               latNorth > WALES_MIN_LAT && latSouth < WALES_MAX_LAT
    }

    fun isInEngland(latSouth: Double, latNorth: Double, lonWest: Double, lonEast: Double): Boolean {
        // England rough bounding box
        return lonEast > -5.7 && lonWest < 1.8 &&
               latNorth > 49.9 && latSouth < 55.8
    }

    suspend fun getAccessLand(
        lonWest: Double, latSouth: Double,
        lonEast: Double, latNorth: Double
    ): List<WalesAccessLand> {
        val bbox = "$lonWest,$latSouth,$lonEast,$latNorth,EPSG:4326"
        val results = mutableListOf<WalesAccessLand>()

        LAYERS.forEach { layer ->
            try {
                val json = api.getAccessLand(typeName = layer, bbox = bbox)
                val parsed = parseAccessLand(json, layer)
                results.addAll(parsed)
            } catch (e: Exception) {
                // Skip failed layers silently
            }
        }
        return results
    }

    private fun parseAccessLand(json: JsonObject, layer: String): List<WalesAccessLand> {
        val features = json.getAsJsonArray("features") ?: return emptyList()
        val results = mutableListOf<WalesAccessLand>()

        features.forEach { featureEl ->
            val feature = featureEl.asJsonObject
            val props   = feature.getAsJsonObject("properties") ?: return@forEach
            val geom    = feature.getAsJsonObject("geometry")   ?: return@forEach

            val rings = mutableListOf<List<Pair<Double, Double>>>()

            // Handle both Polygon and MultiPolygon
            when (geom.get("type")?.asString) {
                "MultiPolygon" -> {
                    geom.getAsJsonArray("coordinates")?.forEach { polyEl ->
                        polyEl.asJsonArray.forEach { ringEl ->
                            val ring = mutableListOf<Pair<Double, Double>>()
                            ringEl.asJsonArray.forEach { ptEl ->
                                val pt = ptEl.asJsonArray
                                ring.add(Pair(pt[0].asDouble, pt[1].asDouble))
                            }
                            rings.add(ring)
                        }
                    }
                }
                "Polygon" -> {
                    geom.getAsJsonArray("coordinates")?.forEach { ringEl ->
                        val ring = mutableListOf<Pair<Double, Double>>()
                        ringEl.asJsonArray.forEach { ptEl ->
                            val pt = ptEl.asJsonArray
                            ring.add(Pair(pt[0].asDouble, pt[1].asDouble))
                        }
                        rings.add(ring)
                    }
                }
            }

            if (rings.isNotEmpty()) {
                results.add(
                    WalesAccessLand(
                        objectId  = props.get("OBJECTID")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                        status    = props.get("EN_STATUS")?.takeIf { !it.isJsonNull }?.asString?.trim() ?: "CRoW Access Land",
                        areaHa    = props.get("AREA_HA")?.takeIf { !it.isJsonNull }?.asDouble ?: 0.0,
                        layerType = layerLabel(layer),
                        rings     = rings
                    )
                )
            }
        }
        return results
    }

    private fun layerLabel(layer: String) = when {
        layer.contains("OPEN_COUNTRY")  -> "Open Country"
        layer.contains("COMMON_LAND")   -> "Common Land"
        layer.contains("PUBLIC_FOREST") -> "Public Forest"
        layer.contains("DEDICATED")     -> "Dedicated Land"
        else -> "Access Land"
    }
}

data class WalesAccessLand(
    val objectId:  Int,
    val status:    String,
    val areaHa:    Double,
    val layerType: String,
    val rings:     List<List<Pair<Double, Double>>>
)
