package com.fanvil.link.sdk

data class FvSdkConfig(
  val userId: String,
  val agoraId: String,
  val agoraAppId: String,
  val accessToken: String,
  val mqttUrl: String,
  val mqttUserName: String,
  val displayName: String? = null,
)
