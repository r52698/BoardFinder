package com.example.org.boardfinder.Services

import android.util.Log
import com.android.volley.Response
import com.android.volley.toolbox.JsonArrayRequest
import com.example.org.boardfinder.Controller.App
import com.example.org.boardfinder.Model.Channel
import com.example.org.boardfinder.Model.Message
import com.example.org.boardfinder.Utilities.URL_GET_CHANNELS
import com.example.org.boardfinder.Utilities.URL_GET_MESSAGES
import org.json.JSONException

object MessageService {
    val channels = ArrayList<Channel>()
    val messages = ArrayList<Message>()
    val sessions = ArrayList<Message>()

    fun getChannels(complete: (Boolean) -> Unit) {
        val channelsRequest = object : JsonArrayRequest(
            Method.GET, URL_GET_CHANNELS, null, Response.Listener { response ->
                println(response)
                try {
                    for (x in 0 until response.length()) {
                        val channel = response.getJSONObject(x)
                        val id = channel.getString("_id")
                        val name = channel.getString("name")
                        val desc = channel.getString("description")
                        val newChannel = Channel(name, desc, id)
                        channels.add(newChannel)
                    }
                    complete(true)
                } catch (e: JSONException) {
                    Log.d("JSON", "EXC: ${e.localizedMessage}")
                    complete(false)
                }
            },
            Response.ErrorListener { error ->
                Log.d("ERROR", "Could not retrieve channels: $error")
                complete(false)
            }) {
            override fun getBodyContentType(): String {
                return "application/json; charset=utf-8"
            }
            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers.put("Authorization", "Bearer ${App.prefs.authToken}")
                return headers
            }
        }
        App.prefs.requestQueue.add(channelsRequest)
    }

    fun getMessages(channelId: String, complete: (Boolean) -> Unit) {
        clearMessages()
        val messagesRequest = object : JsonArrayRequest(
            Method.GET, "$URL_GET_MESSAGES$channelId", null, Response.Listener { response ->
                println(response)
                try {
                    for (x in 0 until response.length()) {
                        val message = response.getJSONObject(x)
                        val id = message.getString("_id")
                        val messageBody = message.getString("messageBody")
                        val channelId = message.getString("channelId")
                        val userName = message.getString("userName")
                        val userAvatar = message.getString("userAvatar")
                        val userAvatarColor = message.getString("userAvatarColor")
                        val timeStamp = message.getString("timeStamp")
                        val newMessage = Message(messageBody, userName, channelId, userAvatar, userAvatarColor, id, timeStamp)
                        messages.add(newMessage)
                    }
                    complete(true)
                } catch (e: JSONException) {
                    Log.d("JSON", "EXC: ${e.localizedMessage}")
                    complete(false)
                }
            },
            Response.ErrorListener { error ->
                Log.d("ERROR", "Could not retrieve messages: $error")
                complete(false)
            }) {
            override fun getBodyContentType(): String {
                return "application/json; charset=utf-8"
            }

            override fun getHeaders(): MutableMap<String, String> {
                val headers = HashMap<String, String>()
                headers.put("Authorization", "Bearer ${App.prefs.authToken}")
                return headers
            }
        }
        App.prefs.requestQueue.add(messagesRequest)
    }

    fun clearMessages() {
        messages.clear()
    }

    fun clearChannels() {
        channels.clear()
    }

    fun contains(channelName: String) : Boolean {
        for (channel in channels) {
            if (channel.name == channelName) return true
        }
        return false
    }

    fun getChannelByName(channelName: String) : Channel? {
        for (channel in channels) {
            if (channel.name == channelName) return channel
        }
        return null
    }

    fun createSessions(sessionTimestamp: String) {
        sessions.clear()
        for (message in messages) {
            if (message.message.startsWith(sessionTimestamp, false)) {
                sessions.add(message)
                println("createSessions($sessionTimestamp) YES: ${message.message}")
            } else {
                println("createSessions($sessionTimestamp) NO : ${message.message}")
            }
        }
    }
}