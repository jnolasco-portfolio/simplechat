package cloud.nolasco.simplechat.tools.datetime;

import java.time.LocalDateTime;
import java.time.LocalTime;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CommonTools {

  @Tool(description = "Get the current date and time in the user's timezone")
  public String getCurrentDateTime() {
    return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
  }

  @Tool(description = "Get a greeting based on the current time")
  public String greeting() {
    int hour = LocalTime.now().getHour();
    if (hour >= 5 && hour < 12) {
      return "Good morning";
    } else if (hour >= 12 && hour < 18) {
      return "Good afternoon";
    } else {
      return "Good evening";
    }
  }

}
