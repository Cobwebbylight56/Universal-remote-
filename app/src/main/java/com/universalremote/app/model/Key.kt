package com.universalremote.app.model

/** Every button the on-screen remote can show. Each TV backend maps these to its own codes. */
enum class Key(val label: String) {
    POWER("Power"), SOURCE("Source"), MUTE("Mute"), HOME("Home"),
    MENU("Menu"), BACK("Back"), EXIT("Exit"), INFO("Info"), GUIDE("Guide"),
    UP("Up"), DOWN("Down"), LEFT("Left"), RIGHT("Right"), OK("OK"),
    VOL_UP("Vol +"), VOL_DOWN("Vol −"), CH_UP("Ch +"), CH_DOWN("Ch −"),
    NUM_0("0"), NUM_1("1"), NUM_2("2"), NUM_3("3"), NUM_4("4"),
    NUM_5("5"), NUM_6("6"), NUM_7("7"), NUM_8("8"), NUM_9("9"),
    PLAY("Play"), PAUSE("Pause"), STOP("Stop"), REWIND("Rewind"), FAST_FORWARD("Fast fwd"),
    RED("Red"), GREEN("Green"), YELLOW("Yellow"), BLUE("Blue"),
    NETFLIX("Netflix"), YOUTUBE("YouTube");

    companion object {
        fun digit(n: Int): Key = valueOf("NUM_$n")
    }
}
