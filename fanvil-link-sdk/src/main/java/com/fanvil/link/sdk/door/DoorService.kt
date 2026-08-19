package com.fanvil.link.sdk.door

import com.fanvil.link.sdk.mqtt.MqttClientHolder
import com.fanvil.link.sdk.mqtt.MqttMsgType
import com.fanvil.link.sdk.mqtt.MqttTopics
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.TimeZone

object DoorService {
  fun openDoor(
    mqtt: MqttClientHolder,
    userId: String,
    mac: String,
    whichDoor: Int = 1,
    doorNoList: List<Int>? = null,
  ) {
    val payload = buildOpenDoorPayload(mac, whichDoor, doorNoList)
    mqtt.publish(MqttTopics.cmdRequest(userId), payload.toString())
  }

  fun buildOpenDoorPayload(
    mac: String,
    whichDoor: Int = 1,
    doorNoList: List<Int>? = null,
    action: Int = 1,
  ): JSONObject {
    val doors = JSONArray()
    if (!doorNoList.isNullOrEmpty()) {
      doorNoList.forEach { index ->
        doors.put(JSONObject().put("action", action).put("index", index))
      }
    } else {
      doors.put(JSONObject().put("action", action).put("index", whichDoor))
    }
    return JSONObject()
      .put("currentTime", formatMqttTime())
      .put("msgType", MqttMsgType.OPEN_DOOR_ACTION)
      .put("msgId", "door_${System.currentTimeMillis()}")
      .put(
        "data",
        JSONObject()
          .put("doors", doors)
          .put("mac", mac),
      )
  }

  private fun formatMqttTime(): String {
    val cal = Calendar.getInstance()
    val tz = TimeZone.getDefault()
    val offsetMs = tz.getOffset(cal.timeInMillis)
    val sign = if (offsetMs >= 0) "+" else "-"
    val abs = kotlin.math.abs(offsetMs) / 60000
    val tzStr = String.format("%s%02d%02d", sign, abs / 60, abs % 60)
    return String.format(
      "%04d-%02d-%02dT%02d:%02d:%02d%s",
      cal.get(Calendar.YEAR),
      cal.get(Calendar.MONTH) + 1,
      cal.get(Calendar.DAY_OF_MONTH),
      cal.get(Calendar.HOUR_OF_DAY),
      cal.get(Calendar.MINUTE),
      cal.get(Calendar.SECOND),
      tzStr,
    )
  }
}
