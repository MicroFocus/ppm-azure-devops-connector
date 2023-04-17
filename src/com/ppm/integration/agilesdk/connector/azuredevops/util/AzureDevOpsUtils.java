package com.ppm.integration.agilesdk.connector.azuredevops.util;

import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;
import com.ppm.integration.agilesdk.connector.azuredevops.model.WorkItemExternalTask;
import org.apache.commons.lang.StringUtils;

import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.stream.Collectors;

public class AzureDevOpsUtils {

    private final static Logger logger = LogManager.getLogger(AzureDevOpsUtils.class);
    private final static SimpleDateFormat shortDateFormat = new SimpleDateFormat("yyyy-MM-dd");
    private final static DateTimeFormatter longDateTimeFormatter = new DateTimeFormatterBuilder()
            // date/time
            .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            // offset (hh:mm - "+00:00" when it's zero)
            .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
            // offset (hhmm - "+0000" when it's zero)
            .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
            // offset (hh - "Z" when it's zero)
            .optionalStart().appendOffset("+HH", "Z").optionalEnd()
            // create formatter
            .toFormatter();


    public static Collection<String> extractStringListParams(String s) {
        if (StringUtils.isBlank(s)) {
            return new ArrayList<>();
        }

        return Arrays.stream(StringUtils.split(s.replace(',', ';'), ';')).map(str -> str.trim()).filter(str -> !StringUtils.isBlank(str)).collect(Collectors.toList());
    }

    public static Date parseDateStr(String dateStr) {

        if (dateStr == null || "null".equalsIgnoreCase(dateStr)) {
            return null;
        }

        try {
            if (dateStr.contains("T")) {
                ZonedDateTime date = ZonedDateTime.parse(dateStr, longDateTimeFormatter);
                return Date.from(date.toInstant());
            } else {
                // Format yyyy-MM-dd
                return shortDateFormat.parse(dateStr);
            }
        } catch (Exception e) {
            logger.error("Failed to parse Date string " + dateStr + " , ignoring date.", e);
            return null;
        }
    }
}
