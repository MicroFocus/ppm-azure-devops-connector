package com.ppm.integration.agilesdk.connector.azuredevops.model;

import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class AzureDevopsObject {


    public String name;
    public String url;
    public String id;
    public String lastUpdateTime;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getLastUpdateTime() {
        return lastUpdateTime;
    }

    public void setLastUpdateTime(String lastUpdateTime) {
        this.lastUpdateTime = lastUpdateTime;
    }


}

