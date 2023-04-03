
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops.rest;


import org.apache.wink.client.ClientConfig;

import java.nio.charset.Charset;
import java.util.Base64;

public class AzureDevopsRestConfig {
    private ClientConfig clientConfig;

    private String authToken;

    private String organizationUrl;

    public ClientConfig getClientConfig() {
        return clientConfig;
    }

    public AzureDevopsRestConfig() {
        clientConfig = new ClientConfig();
    }


    public ClientConfig setProxy(String proxyHost, String proxyPort) {

        if (proxyHost != null && !proxyHost.isEmpty() && proxyPort != null && !proxyPort.isEmpty()) {
            clientConfig.proxyHost(proxyHost);
            clientConfig.proxyPort(Integer.parseInt(proxyPort));
        }
        return clientConfig;
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
}
