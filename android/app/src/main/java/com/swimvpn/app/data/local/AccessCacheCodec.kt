package com.swimvpn.app.data.local

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter
import com.swimvpn.app.data.network.AccessProfileResponse
import com.swimvpn.app.data.network.ServerNode

/**
 * The locally-cached snapshot of the backend access state, used for OFFLINE / API-unreachable
 * resilience: the last successfully-resolved access profile + backend servers, so the app can show
 * and connect to purchased servers when api.swimvpn.pro is blocked or there is no network.
 *
 * [savedAtMs] is the wall-clock time of the snapshot (for an honest "cached, last updated …" label).
 */
data class CachedAccess(
    val profile: AccessProfileResponse,
    val servers: List<ServerNode>,
    val savedAtMs: Long,
)

/**
 * Pure, Android-free (de)serialization for [CachedAccess] — unit-testable without a device.
 * The Android-side at-rest ENCRYPTION + DataStore live in [AccessCacheStore]; this layer only does
 * JSON. Like [com.swimvpn.app.config.ConfigRepository], it tolerates unknown enum constants so a
 * value added/renamed in a newer build never makes Gson throw and silently drops the cache.
 */
object AccessCacheCodec {
    // Mirrors ConfigRepository's EnumFallbackTypeAdapterFactory: map an unknown enum name to a member
    // named UNKNOWN if present, else the first constant — never throw on decode.
    private object EnumFallbackTypeAdapterFactory : TypeAdapterFactory {
        override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
            val rawType = type.rawType
            if (!rawType.isEnum) return null
            val constants = rawType.enumConstants ?: return null
            val byName = HashMap<String, Any>()
            for (c in constants) byName[(c as Enum<*>).name] = c
            val fallback: Any? = byName["UNKNOWN"] ?: constants.firstOrNull()
            return object : TypeAdapter<T>() {
                override fun write(out: JsonWriter, value: T) {
                    if (value == null) out.nullValue() else out.value((value as Enum<*>).name)
                }

                @Suppress("UNCHECKED_CAST")
                override fun read(reader: JsonReader): T {
                    if (reader.peek() == JsonToken.NULL) {
                        reader.nextNull()
                        return null as T
                    }
                    val name = reader.nextString()
                    return (byName[name] ?: fallback) as T
                }
            }
        }
    }

    private val gson: Gson = GsonBuilder()
        .registerTypeAdapterFactory(EnumFallbackTypeAdapterFactory)
        .create()

    fun encode(cache: CachedAccess): String = gson.toJson(cache)

    /** Decode a snapshot; returns null on null/blank input or any parse failure (never throws). */
    fun decode(json: String?): CachedAccess? {
        if (json.isNullOrBlank()) return null
        val parsed = runCatching { gson.fromJson(json, CachedAccess::class.java) }.getOrNull() ?: return null
        // Gson populates fields by reflection and CAN leave a non-null Kotlin field null (e.g. "{}" with
        // no "profile"). The compiler thinks this comparison is always true; at runtime it is not.
        @Suppress("SENSELESS_COMPARISON")
        if (parsed.profile == null) return null
        return parsed
    }
}
