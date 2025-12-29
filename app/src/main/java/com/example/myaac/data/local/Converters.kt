package com.example.myaac.data.local

import androidx.room.TypeConverter
import com.example.myaac.model.AacButton
import com.example.myaac.model.ButtonAction
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonSerializationContext
import com.google.gson.JsonSerializer
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

class Converters {

    private val gson = GsonBuilder()
        .registerTypeAdapter(ButtonAction::class.java, ButtonActionAdapter())
        .create()

    @TypeConverter
    fun fromButtonList(value: List<AacButton>): String {
        return gson.toJson(value)
    }

    @TypeConverter
    fun toButtonList(value: String): List<AacButton> {
        val listType = object : TypeToken<List<AacButton>>() {}.type
        return gson.fromJson(value, listType)
    }

    private class ButtonActionAdapter : JsonSerializer<ButtonAction>, JsonDeserializer<ButtonAction> {
        override fun serialize(src: ButtonAction, typeOfSrc: Type, context: JsonSerializationContext): JsonElement {
            val jsonObject = JsonObject()
            when (src) {
                is ButtonAction.Speak -> {
                    jsonObject.addProperty("type", "speak")
                    jsonObject.addProperty("text", src.text)
                }
                is ButtonAction.LinkToBoard -> {
                    jsonObject.addProperty("type", "link")
                    jsonObject.addProperty("boardId", src.boardId)
                }
                is ButtonAction.LaunchApp -> {
                    jsonObject.addProperty("type", "launch_app")
                    jsonObject.addProperty("packageName", src.packageName)
                }
                ButtonAction.ClearSentence -> jsonObject.addProperty("type", "clear")
                ButtonAction.DeleteLastWord -> jsonObject.addProperty("type", "delete")
            }
            return jsonObject
        }

        override fun deserialize(json: JsonElement, typeOfT: Type, context: JsonDeserializationContext): ButtonAction {
            val jsonObject = json.asJsonObject
            return when (val type = jsonObject.get("type").asString) {
                "speak" -> ButtonAction.Speak(jsonObject.get("text").asString)
                "link" -> ButtonAction.LinkToBoard(jsonObject.get("boardId").asString)
                "launch_app" -> ButtonAction.LaunchApp(jsonObject.get("packageName").asString)
                "clear" -> ButtonAction.ClearSentence
                "delete" -> ButtonAction.DeleteLastWord
                else -> ButtonAction.Speak("Error: Unknown action $type")
            }
        }
    }
}
