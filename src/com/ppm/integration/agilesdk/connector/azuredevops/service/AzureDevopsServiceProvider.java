/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops.service;

import com.ppm.integration.agilesdk.ValueSet;
import com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsConstants;
import com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsIntegrationConnector;
import com.ppm.integration.agilesdk.connector.azuredevops.rest.AzureDevopsRestClient;
import com.ppm.integration.agilesdk.connector.azuredevops.rest.AzureDevopsRestConfig;
import com.ppm.integration.agilesdk.provider.Providers;
import com.ppm.integration.agilesdk.provider.UserProvider;
import org.apache.commons.lang.StringUtils;

public class AzureDevopsServiceProvider {

    public static UserProvider getUserProvider() {
        return Providers.getUserProvider(AzureDevopsIntegrationConnector.class);
    }

    public static AzureDevopsService get(ValueSet config) {

        String proxyHost = config.get(AzureDevopsConstants.KEY_PROXY_HOST);

        AzureDevopsRestConfig restConfig = new AzureDevopsRestConfig();

        if (!StringUtils.isBlank(proxyHost)) {
            String proxyPort = config.get(AzureDevopsConstants.KEY_PROXY_PORT);
            if (StringUtils.isBlank(proxyPort)) {
                proxyPort = "80";
            }

            restConfig.setProxy(proxyHost, proxyPort);
        }
        restConfig.setAuthToken(getPersonalAccessToken(config));

        String organizationUrl = config.get(AzureDevopsConstants.KEY_ORGANIZATION_URL);

        restConfig.setOrganizationUrl(organizationUrl);

        return new AzureDevopsService(new AzureDevopsRestClient(restConfig));
    }

    public static String getPersonalAccessToken(ValueSet config) {
        String integrationToken = config.get(AzureDevopsConstants.KEY_PERSONAL_ACCESS_TOKEN);
        if (!StringUtils.isBlank(integrationToken)) {
            return integrationToken;
        } else {
            throw new RuntimeException("Missing Personal Access Token");
        }
    }

}
