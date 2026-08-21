package com.fanvil.link.sdk.rtc

enum class RtcEvent {
  ConnectionChanged,
  JoinChannel,
  FirstVideoFrame,
  VideoStateChanged,
  LeaveChannel,
  UserOffline,
  SnapshotTaken,
  TokenWillExpire,
}
