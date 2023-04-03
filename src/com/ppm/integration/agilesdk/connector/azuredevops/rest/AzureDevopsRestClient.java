
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops.rest;


import com.kintana.core.logging.LogLevel;
import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;
import com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsConstants;
import org.apache.commons.lang.StringUtils;
import org.apache.wink.client.ClientConfig;
import org.apache.wink.client.ClientResponse;
import org.apache.wink.client.Resource;
import org.apache.wink.client.RestClient;

import javax.ws.rs.core.MediaType;
import java.io.UnsupportedEncodingException;
import java.net.*;
import java.util.UUID;

public class AzureDevopsRestClient {

    private final static Logger logger = LogManager.getLogger(AzureDevopsRestClient.class);

    private RestClient restClient;
    private AzureDevopsRestConfig restConfig;
    private ClientConfig clientConfig;

    public AzureDevopsRestClient(AzureDevopsRestConfig notionConfig) {
        this.restConfig = notionConfig;
        this.clientConfig = notionConfig.getClientConfig();
        this.restClient = new RestClient(clientConfig);
    }

    private Resource getAzureDevopsResource(String relativeUrl) {
        Resource resource;

        String fullUrl = AzureDevopsConstants.API_ROOT_URL + restConfig.getOrganizationUrl() + relativeUrl;

        logger.debug("GET url: " + fullUrl);

        resource = restClient.resource(fullUrl).accept(MediaType.APPLICATION_JSON).header("Authorization", restConfig.getBasicAuthorizationHeaderValue());

        // Following header is required for easy HTTP request tracing in systems such as DataPower.
        {
            resource.header("X-B3-TraceId", UUID.randomUUID().toString());
        }


        return resource;
    }

    public ClientResponse sendGet(String uri) {

        Resource resource = this.getAzureDevopsResource(uri);
        ClientResponse response = resource.get();

        checkResponseStatus(200, response, uri, "GET", null);

        return response;
    }

    private void checkResponseStatus(int expectedHttpStatusCode, ClientResponse response, String uri, String verb, String payload) {

        if (response.getStatusCode() != expectedHttpStatusCode) {
            StringBuilder errorMessage = new StringBuilder(String.format("## Unexpected HTTP response status code %s for %s uri %s, expected %s", response.getStatusCode(), verb, uri, expectedHttpStatusCode));

            if (payload != null) {
                errorMessage.append(System.lineSeparator()).append(System.lineSeparator()).append("# Sent Payload:").append(System.lineSeparator()).append(payload);
            }
            String responseStr = null;
            try {
                responseStr = response.getEntity(String.class);
            } catch (Exception e) {
                // we don't do anything if we cannot get the response.
            }
            if (!StringUtils.isBlank(responseStr)) {
                errorMessage.append(System.lineSeparator()).append(System.lineSeparator()).append("# Received Response:").append(System.lineSeparator()).append(responseStr);
            }

            throw new RestRequestException(response.getStatusCode(), errorMessage.toString());
        }
    }

    public ClientResponse sendPost(String uri, String jsonPayload, int expectedHttpStatusCode) {
        Resource resource = this.getAzureDevopsResource(uri);
        resource.contentType(MediaType.APPLICATION_JSON);
        ClientResponse response = resource.post(jsonPayload);
        checkResponseStatus(expectedHttpStatusCode, response, uri, "POST", jsonPayload);

        return response;
    }

    /*public ClientResponse sendPut(String uri, String jsonPayload, int expectedHttpStatusCode) {

        if (ENABLE_REST_CALLS_STATUS_LOG) {
            logger.log(LogLevel.STATUS, "PUT "+uri);
        }

        String uuid = UUID.randomUUID().toString();
        Resource resource = this.getAzureDevopsResource(uri,true, uuid);
        ClientResponse response = resource.put(jsonPayload);

        checkResponseStatus(expectedHttpStatusCode, response, uri, "PUT", jsonPayload, uuid);

        return response;
    }*/

}
