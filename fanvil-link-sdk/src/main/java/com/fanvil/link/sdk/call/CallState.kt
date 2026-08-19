package com.fanvil.link.sdk.call

enum class CallState {
  Incoming,
  IncomingEarlyMedia,
  OutgoingInit,
  Connected,
  End,
  Released,
  Error,
  UpdatedByRemote,
}
