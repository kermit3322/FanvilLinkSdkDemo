package com.fanvil.link.sdk.mqtt

object MqttTopics {
  fun cmdRequest(userId: String) = "vendor/app/$userId/cmd/request"
  fun cmdAck(userId: String) = "vendor/app/$userId/cmd/ack"
  fun notify(userId: String) = "vendor/app/$userId/notify"
  fun tokenRequest(userId: String) = "vendor/fvIot/$userId/token/request"
  fun tokenAck(userId: String) = "vendor/fvIot/$userId/token/ack"
  fun sipUp(agoraId: String) = "vendor/call/proxy/sip/up/$agoraId"
  fun sipDown(agoraId: String) = "vendor/call/proxy/sip/down/$agoraId"
  fun msgCallDown(agoraId: String) = "vendor/call/proxy/msg/down/$agoraId"
}

object MqttMsgType {
  const val OPEN_DOOR_ACTION = "app_open_door_act"
  const val OPEN_DOOR_ACK = "app_open_door_ack"
}

fun buildMqttUsername(userName: String): String {
  if (userName.isEmpty()) return ""
  return if (userName.startsWith("APP_")) userName else "APP_$userName"
}

fun defaultSubscribeTopics(userId: String, agoraId: String): List<String> {
  return listOf(
    MqttTopics.cmdAck(userId),
    MqttTopics.notify(userId),
    MqttTopics.tokenAck(userId),
    MqttTopics.sipDown(agoraId),
    MqttTopics.msgCallDown(agoraId),
  )
}
