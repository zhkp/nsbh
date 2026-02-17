package com.kp.nsbh.tools;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
@NsbhTool(
        name = "time",
        description = "Returns current server time in ISO-8601 format",
        schema = "{}",
        requiredPermissions = {}
)
public class TimeTool implements Tool {
    @Override
    public String execute(String inputJson) {
        return ZonedDateTime.now().format(DateTimeFormatter.ISO_ZONED_DATE_TIME);
    }
}
