package com.chakir.plexhubtv.core.database

import com.chakir.plexhubtv.domain.model.*
import com.google.gson.*
import java.lang.reflect.Type

class MediaStreamAdapter : JsonSerializer<MediaStream>, JsonDeserializer<MediaStream> {
    override fun serialize(src: MediaStream, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
        val jsonObject = context.serialize(src).asJsonObject
        val type = when (src) {
            is AudioStream -> "audio"
            is VideoStream -> "video"
            is SubtitleStream -> "subtitle"
            is UnknownStream -> "unknown"
        }
        jsonObject.addProperty("stream_type_key", type)
        return jsonObject
    }

    override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): MediaStream {
        val jsonObject = json.asJsonObject
        val type = jsonObject.get("stream_type_key")?.asString ?: "unknown"
        
        return when (type) {
            "audio" -> context.deserialize(json, AudioStream::class.java)
            "video" -> context.deserialize(json, VideoStream::class.java)
            "subtitle" -> context.deserialize(json, SubtitleStream::class.java)
            else -> context.deserialize(json, UnknownStream::class.java)
        }
    }
}
