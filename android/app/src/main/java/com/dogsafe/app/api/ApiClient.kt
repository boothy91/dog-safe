package com.dogsafe.app.api

import com.dogsafe.app.model.Restriction
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

interface NaturalEnglandApi {
    @GET("OASYS_RESTRICTION_PRD_VIEW/FeatureServer/0/query")
    suspend fun getRestrictions(
        @Query("geometry")        geometry: String,
        @Query("geometryType")    geometryType: String = "esriGeometryEnvelope",
        @Query("inSR")            inSR: String = "4326",
        @Query("spatialRel")      spatialRel: String = "esriSpatialRelIntersects",
        @Query("where")           where: String,
        @Query("outFields")       outFields: String = "CASE_NUMBER,TYPE,PURPOSE,START_DATE,END_DATE,VIEW_MAP",
        @Query("outSR")           outSR: String = "4326",
        @Query("returnGeometry")  returnGeometry: Boolean = true,
        @Query("f")               format: String = "json"
    ): JsonObject
}

object ApiClient {
    private const val BASE_URL =
        "https://services.arcgis.com/JJzESW51TqeY9uat/arcgis/rest/services/"

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val api: NaturalEnglandApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(NaturalEnglandApi::class.java)

    // Dog restriction type codes
    const val DOG_RESTRICTION_TYPES =
        "(TYPE='02' OR TYPE='04' OR TYPE='03' OR TYPE='05' OR TYPE='09' OR TYPE='10')"

    // Active restrictions filter
    const val ACTIVE_FILTER =
        " AND END_DATE>=CURRENT_TIMESTAMP AND START_DATE<=CURRENT_TIMESTAMP"

    fun parseRestrictions(json: JsonObject): List<Restriction> {
        val features = json.getAsJsonArray("features") ?: return emptyList()
        val seen = mutableMapOf<String, Restriction>()

        features.forEach { featureEl ->
            val feature = featureEl.asJsonObject
            val attrs   = feature.getAsJsonObject("attributes")
            val geom    = feature.getAsJsonObject("geometry")

            val caseNum = attrs.get("CASE_NUMBER")?.takeIf { !it.isJsonNull }?.asString ?: return@forEach
            val endDate = attrs.get("END_DATE")?.takeIf { !it.isJsonNull }?.asLong

            // Keep most recent for duplicate case numbers
            if (seen.containsKey(caseNum) &&
                (seen[caseNum]?.endDate ?: 0) >= (endDate ?: 0)) return@forEach

            val rings = mutableListOf<List<Pair<Double, Double>>>()
            geom?.getAsJsonArray("rings")?.forEach { ringEl ->
                val ring = mutableListOf<Pair<Double, Double>>()
                ringEl.asJsonArray.forEach { pointEl ->
                    val pt = pointEl.asJsonArray
                    ring.add(Pair(pt[0].asDouble, pt[1].asDouble))
                }
                rings.add(ring)
            }

            seen[caseNum] = Restriction(
                objectId   = attrs.get("OBJECTID")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                caseNumber = caseNum,
                type       = attrs.get("TYPE")?.takeIf { !it.isJsonNull }?.asString ?: "99",
                purpose    = attrs.get("PURPOSE")?.takeIf { !it.isJsonNull }?.asString,
                startDate  = attrs.get("START_DATE")?.takeIf { !it.isJsonNull }?.asLong,
                endDate    = endDate,
                viewMap    = attrs.get("VIEW_MAP")?.takeIf { !it.isJsonNull }?.asString,
                rings      = rings
            )
        }
        return seen.values.toList()
    }
}
