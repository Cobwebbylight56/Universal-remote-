package com.universalremote.app.control

import com.universalremote.app.model.Key
import com.universalremote.app.model.Key.*

/**
 * Default button codes for each backend. Any of these can be overridden per TV
 * from the "Codes" screen, so a wrong guess here is easy to fix on the device.
 */
object CodeTables {

    /**
     * Vestel "SmartCenter" key codes (HTTP POST to port 56789).
     * Power, mute, volume, channel and digits are confirmed by public scripts.
     * The rest follow Vestel's RC5-style numbering and are best guesses:
     * use the Code Explorer to check and fix them on your own TV.
     */
    val vestel: Map<Key, Int> = buildMap {
        for (n in 0..9) put(Key.digit(n), 1000 + n)
        put(POWER, 1012); put(MUTE, 1013); put(VOL_UP, 1016); put(VOL_DOWN, 1017)
        put(CH_UP, 1032); put(CH_DOWN, 1033)
        put(HOME, 1046)
        put(UP, 1020); put(DOWN, 1019); put(LEFT, 1021); put(RIGHT, 1022); put(OK, 1053)
        put(BACK, 1010); put(MENU, 1048); put(EXIT, 1037); put(SOURCE, 1056)
        put(INFO, 1015); put(GUIDE, 1047)
        put(RED, 1055); put(GREEN, 1054); put(YELLOW, 1050); put(BLUE, 1052)
        put(PLAY, 1014); put(PAUSE, 1049); put(STOP, 1024); put(REWIND, 1041); put(FAST_FORWARD, 1040)
        put(NETFLIX, 1066); put(YOUTUBE, 1061)
    }

    /** Roku External Control Protocol key names ("launch/<id>" launches a channel). */
    val roku: Map<Key, String> = buildMap {
        for (n in 0..9) put(Key.digit(n), "Lit_$n")
        put(POWER, "Power"); put(HOME, "Home"); put(BACK, "Back"); put(EXIT, "Home")
        put(UP, "Up"); put(DOWN, "Down"); put(LEFT, "Left"); put(RIGHT, "Right"); put(OK, "Select")
        put(VOL_UP, "VolumeUp"); put(VOL_DOWN, "VolumeDown"); put(MUTE, "VolumeMute")
        put(CH_UP, "ChannelUp"); put(CH_DOWN, "ChannelDown")
        put(INFO, "Info"); put(MENU, "Info"); put(SOURCE, "InputHDMI1")
        put(PLAY, "Play"); put(PAUSE, "Play"); put(REWIND, "Rev"); put(FAST_FORWARD, "Fwd")
        put(NETFLIX, "launch/12"); put(YOUTUBE, "launch/837")
    }

    /** Samsung Tizen remote key names ("app:<id>" launches an app). */
    val samsung: Map<Key, String> = buildMap {
        for (n in 0..9) put(Key.digit(n), "KEY_$n")
        put(POWER, "KEY_POWER"); put(SOURCE, "KEY_SOURCE"); put(MUTE, "KEY_MUTE"); put(HOME, "KEY_HOME")
        put(MENU, "KEY_MENU"); put(BACK, "KEY_RETURN"); put(EXIT, "KEY_EXIT"); put(INFO, "KEY_INFO")
        put(GUIDE, "KEY_GUIDE")
        put(UP, "KEY_UP"); put(DOWN, "KEY_DOWN"); put(LEFT, "KEY_LEFT"); put(RIGHT, "KEY_RIGHT"); put(OK, "KEY_ENTER")
        put(VOL_UP, "KEY_VOLUP"); put(VOL_DOWN, "KEY_VOLDOWN"); put(CH_UP, "KEY_CHUP"); put(CH_DOWN, "KEY_CHDOWN")
        put(PLAY, "KEY_PLAY"); put(PAUSE, "KEY_PAUSE"); put(STOP, "KEY_STOP")
        put(REWIND, "KEY_REWIND"); put(FAST_FORWARD, "KEY_FF")
        put(RED, "KEY_RED"); put(GREEN, "KEY_GREEN"); put(YELLOW, "KEY_YELLOW"); put(BLUE, "KEY_CYAN")
        put(NETFLIX, "app:3201907018807"); put(YOUTUBE, "app:111299001912")
    }

    /**
     * LG webOS: plain names go to the pointer-input socket as button presses,
     * "ssap://…" are API calls and "app:<id>" launches an app.
     */
    val lg: Map<Key, String> = buildMap {
        for (n in 0..9) put(Key.digit(n), "$n")
        put(POWER, "ssap://system/turnOff"); put(SOURCE, "app:com.webos.app.inputpicker")
        put(MUTE, "MUTE"); put(HOME, "HOME"); put(MENU, "MENU"); put(BACK, "BACK"); put(EXIT, "EXIT")
        put(INFO, "INFO"); put(GUIDE, "GUIDE")
        put(UP, "UP"); put(DOWN, "DOWN"); put(LEFT, "LEFT"); put(RIGHT, "RIGHT"); put(OK, "ENTER")
        put(VOL_UP, "VOLUMEUP"); put(VOL_DOWN, "VOLUMEDOWN"); put(CH_UP, "CHANNELUP"); put(CH_DOWN, "CHANNELDOWN")
        put(PLAY, "PLAY"); put(PAUSE, "PAUSE"); put(STOP, "STOP"); put(REWIND, "REWIND"); put(FAST_FORWARD, "FASTFORWARD")
        put(RED, "RED"); put(GREEN, "GREEN"); put(YELLOW, "YELLOW"); put(BLUE, "BLUE")
        put(NETFLIX, "app:netflix"); put(YOUTUBE, "app:youtube.leanback.v4")
    }

    /** Sony Bravia IRCC codes. */
    val sony: Map<Key, String> = buildMap {
        put(NUM_1, "AAAAAQAAAAEAAAAAAw=="); put(NUM_2, "AAAAAQAAAAEAAAABAw=="); put(NUM_3, "AAAAAQAAAAEAAAACAw==")
        put(NUM_4, "AAAAAQAAAAEAAAADAw=="); put(NUM_5, "AAAAAQAAAAEAAAAEAw=="); put(NUM_6, "AAAAAQAAAAEAAAAFAw==")
        put(NUM_7, "AAAAAQAAAAEAAAAGAw=="); put(NUM_8, "AAAAAQAAAAEAAAAHAw=="); put(NUM_9, "AAAAAQAAAAEAAAAIAw==")
        put(NUM_0, "AAAAAQAAAAEAAAAJAw==")
        put(POWER, "AAAAAQAAAAEAAAAVAw=="); put(SOURCE, "AAAAAQAAAAEAAAAlAw=="); put(MUTE, "AAAAAQAAAAEAAAAUAw==")
        put(HOME, "AAAAAQAAAAEAAABgAw=="); put(MENU, "AAAAAgAAAJcAAAA2Aw=="); put(BACK, "AAAAAgAAAJcAAAAjAw==")
        put(EXIT, "AAAAAQAAAAEAAABjAw=="); put(INFO, "AAAAAQAAAAEAAAA6Aw=="); put(GUIDE, "AAAAAgAAAKQAAABbAw==")
        put(UP, "AAAAAQAAAAEAAAB0Aw=="); put(DOWN, "AAAAAQAAAAEAAAB1Aw=="); put(LEFT, "AAAAAQAAAAEAAAA0Aw==")
        put(RIGHT, "AAAAAQAAAAEAAAAzAw=="); put(OK, "AAAAAQAAAAEAAABlAw==")
        put(VOL_UP, "AAAAAQAAAAEAAAASAw=="); put(VOL_DOWN, "AAAAAQAAAAEAAAATAw==")
        put(CH_UP, "AAAAAQAAAAEAAAAQAw=="); put(CH_DOWN, "AAAAAQAAAAEAAAARAw==")
        put(PLAY, "AAAAAgAAAJcAAAAaAw=="); put(PAUSE, "AAAAAgAAAJcAAAAZAw=="); put(STOP, "AAAAAgAAAJcAAAAYAw==")
        put(REWIND, "AAAAAgAAAJcAAAAbAw=="); put(FAST_FORWARD, "AAAAAgAAAJcAAAAcAw==")
        put(RED, "AAAAAgAAAJcAAAAlAw=="); put(GREEN, "AAAAAgAAAJcAAAAmAw==")
        put(YELLOW, "AAAAAgAAAJcAAAAnAw=="); put(BLUE, "AAAAAgAAAJcAAAAkAw==")
        put(NETFLIX, "AAAAAgAAABoAAAB8Aw==")
    }
}
