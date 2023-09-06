
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops.rest;


import com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsConstants;
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
        if (organizationUrl.toLowerCase().startsWith("https://") || organizationUrl.toLowerCase().startsWith("http://")) {
            // The full URL was captured, so they should be using TFS On-Prem.
            // Note that even this may work, it is not officially supported unless issues can also be reproduced on Azure DevOps Cloud.

            // We make sure that there's no trailing '/' as we'll append it as part of relative urls.
            return StringUtils.stripEnd(organizationUrl.trim(), "/");
        } else {
            // Only the organization was entered, so they're using Azure DevOps Cloud
            return AzureDevopsConstants.API_ROOT_URL + organizationUrl;
        }
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
