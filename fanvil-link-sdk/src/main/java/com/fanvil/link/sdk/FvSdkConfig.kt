package com.fanvil.link.sdk

data class FvSdkConfig(
  /** 用户 ID，MQTT clientId */
  val userId: String,
  /** SIP / RTC 账号（agoraId） */
  val agoraId: String,
  /** RTC AppId */
  val agoraAppId: String,
  /** MQTT 密码（accessToken） */
  val accessToken: String,
  /** MQTT 地址，如 ssl://host:8883 */
  val mqttUrl: String,
  /** MQTT 用户名 */
  val mqttUserName: String,
  /** SIP 显示名，可选 */
  val displayName: String? = null,
)
