
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops.rest;


import org.apache.commons.lang.StringUtils;
import org.apache.wink.client.ClientConfig;

import java.nio.charset.Charset;
import java.util.Base64;

public class AzureDevopsRestConfig {

    private String authToken;

    private String organizationUrl;


    private String proxyHost;
    private String proxyPort;


    public AzureDevopsRestConfig setProxy(String proxyHost, String proxyPort) {

        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort != null && !proxyPort.isEmpty()) {
            this.proxyHost = proxyHost;
            this.proxyPort = proxyPort;
        }
        return this;
    }


    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public String getOrganizationUrl() {
        return organizationUrl;
    }

    public void setOrganizationUrl(String organizationUrl) {
        this.organizationUrl = organizationUrl;
    }


    public String getBasicAuthorizationHeaderValue() {
        // Username is irrelevant for PAT authentication
        return "Basic " + Base64.getEncoder().encodeToString((":"+getAuthToken()).getBytes());
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public int getProxyPort() {
        if (StringUtils.isBlank(proxyPort) || !StringUtils.isNumeric(proxyPort)) {
            return 8080;
        }
        return Integer.parseInt(proxyPort);
    }

}
