package com.fanvil.link.sdk.mqtt

import android.util.Log
import org.eclipse.paho.mqttv5.client.IMqttMessageListener
import org.eclipse.paho.mqttv5.client.IMqttToken
import org.eclipse.paho.mqttv5.client.MqttActionListener
import org.eclipse.paho.mqttv5.client.MqttAsyncClient
import org.eclipse.paho.mqttv5.client.MqttCallback
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence
import org.eclipse.paho.mqttv5.common.MqttException
import org.eclipse.paho.mqttv5.common.MqttMessage
import org.eclipse.paho.mqttv5.common.MqttSubscription
import org.eclipse.paho.mqttv5.common.packet.MqttProperties
import java.util.concurrent.CopyOnWriteArrayList

class MqttClientHolder(
  private val onConnectionChanged: (status: String, code: Int?, message: String?, reconnect: Boolean?) -> Unit,
  private val onMessage: (topic: String, payload: String) -> Unit,
) {
  companion object {
    private const val TAG = "FvMqtt"
    const val QOS_UNRELIABLE = 0
  }

  @Volatile
  private var client: MqttAsyncClient? = null

  private val subscribedTopics = CopyOnWriteArrayList<String>()

  fun isConnected(): Boolean = client?.isConnected == true

  fun connect(
    url: String,
    clientId: String,
    username: String,
    password: String,
    keepAlive: Int = 60,
    connectionTimeout: Int = 30,
    cleanStart: Boolean = true,
  ) {
    disconnectInternal()

    val options = MqttConnectionOptions().apply {
      isAutomaticReconnect = false
      userName = username
      setPassword(password.toByteArray())
      keepAliveInterval = keepAlive
      this.connectionTimeout = connectionTimeout
      isCleanStart = cleanStart
      sessionExpiryInterval = 0L
      isHttpsHostnameVerificationEnabled = false
    }

    val mqttClient = MqttAsyncClient(url, clientId, MemoryPersistence())
    client = mqttClient

    mqttClient.setCallback(object : MqttCallback {
      override fun disconnected(disconnectResponse: MqttDisconnectResponse) {
        val code = disconnectResponse.returnCode
        onConnectionChanged("disconnected", code, disconnectResponse.exception?.message, null)
      }

      override fun mqttErrorOccurred(exception: MqttException) {
        Log.e(TAG, "mqttErrorOccurred", exception)
      }

      override fun messageArrived(topic: String?, message: MqttMessage?) {}

      override fun deliveryComplete(token: IMqttToken?) {}

      override fun connectComplete(reconnect: Boolean, serverURI: String?) {
        onConnectionChanged("connected", null, serverURI, reconnect)
        if (subscribedTopics.isNotEmpty()) {
          subscribe(subscribedTopics.toList())
        }
      }

      override fun authPacketArrived(reasonCode: Int, properties: MqttProperties?) {}
    })

    mqttClient.connect(options, null, object : MqttActionListener {
      override fun onSuccess(asyncActionToken: IMqttToken?) {
        Log.i(TAG, "connect success")
        onConnectionChanged("connected", null, null, false)
        if (subscribedTopics.isNotEmpty()) {
          subscribe(subscribedTopics.toList())
        }
      }

      override fun onFailure(asyncActionToken: IMqttToken?, exception: Throwable?) {
        Log.e(TAG, "connect failed", exception)
        onConnectionChanged("failed", null, exception?.message, null)
      }
    })
  }

  fun subscribe(topics: List<String>) {
    if (topics.isEmpty()) return

    topics.forEach { topic ->
      if (!subscribedTopics.contains(topic)) {
        subscribedTopics.add(topic)
      }
    }

    val mqttClient = client ?: run {
      Log.w(TAG, "subscribe queued (client null) topics=$topics")
      return
    }
    if (!mqttClient.isConnected) {
      Log.w(TAG, "subscribe queued (not connected) topics=$topics")
      return
    }

    try {
      val subscriptions = topics.map { MqttSubscription(it, QOS_UNRELIABLE) }.toTypedArray()
      val listeners = Array(topics.size) {
        IMqttMessageListener { topic, message ->
          val payload = message?.payload?.toString(Charsets.UTF_8).orEmpty()
          onMessage(topic.orEmpty(), payload)
        }
      }
      mqttClient.subscribe(subscriptions, null, null, listeners, MqttProperties())
      Log.i(TAG, "subscribe ok topics=$topics")
    } catch (e: Exception) {
      Log.e(TAG, "subscribe failed", e)
      throw e
    }
  }

  fun unsubscribe(topics: List<String>) {
    val mqttClient = client ?: return
    if (topics.isEmpty()) return
    try {
      mqttClient.unsubscribe(topics.toTypedArray())
      subscribedTopics.removeAll(topics.toSet())
    } catch (e: Exception) {
      Log.e(TAG, "unsubscribe failed", e)
      throw e
    }
  }

  fun publish(topic: String, payload: String, qos: Int = QOS_UNRELIABLE, retained: Boolean = false) {
    val mqttClient = client ?: throw IllegalStateException("MQTT not connected")
    if (!mqttClient.isConnected) throw IllegalStateException("MQTT not connected")
    val message = MqttMessage(payload.toByteArray(Charsets.UTF_8)).apply {
      this.qos = qos
      isRetained = retained
    }
    mqttClient.publish(topic, message)
  }

  fun disconnect() {
    disconnectInternal()
    onConnectionChanged("disconnected", null, null, null)
  }

  private fun disconnectInternal() {
    try {
      client?.disconnect()
    } catch (_: Exception) {
    }
    try {
      client?.close()
    } catch (_: Exception) {
    }
    client = null
    subscribedTopics.clear()
  }
}
