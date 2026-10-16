package de.beastsmp.hud;
enum PlayerSetting {
 CHAT("Player chat"), JOIN_MESSAGES("Join / leave"), TIPS("Lobby tips"), WELCOME_CHAT("Welcome chat"),
 HUD("HUD"), ANIMATIONS("Flying symbols"), SOUNDS("Beast sounds"), WELCOME_TITLE("Welcome title");
 final String label;PlayerSetting(String label){this.label=label;}
}
