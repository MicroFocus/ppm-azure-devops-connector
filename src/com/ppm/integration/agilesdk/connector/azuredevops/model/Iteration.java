package com.ppm.integration.agilesdk.connector.azuredevops.model;

import org.apache.commons.lang.StringUtils;

import java.util.Date;

import static com.ppm.integration.agilesdk.connector.azuredevops.util.AzureDevOpsUtils.parseDateStr;

public class Iteration extends AzureDevopsObject {

    private String path;

    private IterationAttributes attributes;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public IterationAttributes getAttributes() {
        return attributes;
    }

    public void setAttributes(IterationAttributes attributes) {
        this.attributes = attributes;
    }


    public class IterationAttributes {
        private String startDate;

        private String finishDate;

        private String timeFrame;

        public String getStartDate() {
            return startDate;
        }

        public void setStartDate(String startDate) {
            this.startDate = startDate;
        }

        public String getFinishDate() {
            return finishDate;
        }

        public void setFinishDate(String finishDate) {
            this.finishDate = finishDate;
        }

        public String getTimeFrame() {
            return timeFrame;
        }

        public void setTimeFrame(String timeFrame) {
            this.timeFrame = timeFrame;
        }
    }

    public Date getStartDate() {
        if (attributes == null || StringUtils.isBlank(attributes.getStartDate())) {
            return null;
        }

        return parseDateStr(attributes.getStartDate());
    }

    public Date getFinishDate() {
        if (attributes == null || StringUtils.isBlank(attributes.getFinishDate())) {
            return null;
        }

        return parseDateStr(attributes.getFinishDate());
    }
}
