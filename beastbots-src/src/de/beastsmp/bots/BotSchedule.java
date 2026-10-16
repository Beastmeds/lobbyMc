package de.beastsmp.bots;
import java.time.LocalTime;
final class BotSchedule {
 static int target(LocalTime now,LocalTime day,LocalTime night,int dayAmount,int nightAmount){
  if(day.equals(night))throw new IllegalArgumentException("Day and night must start at different times");
  boolean daytime=day.isBefore(night)?!now.isBefore(day)&&now.isBefore(night):!now.isBefore(day)||now.isBefore(night);
  return daytime?dayAmount:nightAmount;
 }
}
