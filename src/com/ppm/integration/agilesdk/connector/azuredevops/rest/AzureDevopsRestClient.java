
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops.rest;


import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;
import com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsConstants;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.*;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;


import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Unlike the other AgileSDK connectors that use Wink REST Client, AzureDevOps uses Apache HttpClient (which is only bundled in PPM 2023+) because
 * Azure DevOps REST API requires HTTP PATCH to update work items, and Java HttpUrlConnection (used in Wink REST Client) doesn't support it...
 * */
public class AzureDevopsRestClient {

    private final static Logger logger = LogManager.getLogger(AzureDevopsRestClient.class);

    private AzureDevopsRestConfig restConfig;


    public AzureDevopsRestClient(AzureDevopsRestConfig config) {
        this.restConfig = config;
    }

    // Azure DevOps REST API needs a very specific content-type when doing PATCH.
    private String executeHttpRequest(String relativeUrl, String httpMethod, String jsonPayload, boolean usePatchJsonContentType) {

        String fullUrl = restConfig.getOrganizationUrl() + relativeUrl;

        logger.debug("url: " + fullUrl);

        HttpRequestBase httpRequest = null;

        switch(httpMethod) {
            case "POST":
                httpRequest = new HttpPost(fullUrl);
                if (jsonPayload != null) {
                    ((HttpPost)httpRequest).setEntity(new StringEntity(
                            jsonPayload,
                            usePatchJsonContentType ? ContentType.create("application/json-patch+json",StandardCharsets.UTF_8) : ContentType.APPLICATION_JSON));
                }
                break;
            case "PATCH":
                httpRequest = new HttpPatch(fullUrl);
                if (jsonPayload != null) {
                    ((HttpPatch)httpRequest).setEntity(new StringEntity(
                            jsonPayload,
                            usePatchJsonContentType ? ContentType.create("application/json-patch+json", StandardCharsets.UTF_8) : ContentType.APPLICATION_JSON));
                }
                break;
            default: // GET
                httpRequest = new HttpGet(fullUrl);
                break;
        }

        httpRequest.addHeader(new BasicHeader("accept", MediaType.APPLICATION_JSON));
        httpRequest.addHeader(new BasicHeader("Authorization", restConfig.getBasicAuthorizationHeaderValue()));
        // Following header is required for easy HTTP request tracing in systems such as DataPower.
        httpRequest.addHeader(new BasicHeader("X-B3-TraceId", UUID.randomUUID().toString()));


        // Proxy setting
        if (!StringUtils.isBlank(restConfig.getProxyHost())) {
            HttpHost proxy = new HttpHost(restConfig.getProxyHost(), restConfig.getProxyPort(), "http");

            RequestConfig config = RequestConfig.custom()
                    .setProxy(proxy)
                    .build();
            httpRequest.setConfig(config);
        }


        // HTTP Request is ready. Let's execute it now.
        CloseableHttpResponse response = null;
        CloseableHttpClient httpClient = HttpClients.createDefault();

        try {
            response = httpClient.execute(httpRequest);

            // All Azure DevOps REST calls should return HTTP 200 status code if successful.
            checkResponseStatus(200, response, fullUrl, httpMethod, jsonPayload);

            HttpEntity responseContent = response.getEntity();

            if (responseContent != null) {
                return EntityUtils.toString(responseContent);
            } else {
                return "";
            }
        } catch (Exception e) {
            throw new RuntimeException("Error occurred when making REST call to " + fullUrl, e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (IOException e) {
                    // Do nothing.
                }
            }
            if (httpClient != null) {
                try {
                    httpClient.close();
                } catch (IOException e) {
                    // Do nothing.
                }
            }
        }

    }

    public String sendGet(String uri) {
        return executeHttpRequest(uri, "GET", null, false);
    }

    private void checkResponseStatus(int expectedHttpStatusCode, CloseableHttpResponse response, String uri, String verb, String payload) {

        if (response.getStatusLine().getStatusCode() != expectedHttpStatusCode) {
            StringBuilder errorMessage = new StringBuilder(String.format("## Unexpected HTTP response status code %s for %s uri %s, expected %s", response.getStatusLine().getStatusCode(), verb, uri, expectedHttpStatusCode));

            if (payload != null) {
                errorMessage.append(System.lineSeparator()).append(System.lineSeparator()).append("# Sent Payload:").append(System.lineSeparator()).append(payload);
            }
            String responseStr = null;
            try {
                HttpEntity responseContent = response.getEntity();

                if (responseContent != null) {
                    responseStr = EntityUtils.toString(responseContent);
                }
            } catch (Exception e) {
                // we don't do anything if we cannot get the response.
            }
            if (!StringUtils.isBlank(responseStr)) {
                errorMessage.append(System.lineSeparator()).append(System.lineSeparator()).append("# Received Response:").append(System.lineSeparator()).append(responseStr);
            }

            throw new RestRequestException(response.getStatusLine().getStatusCode(), errorMessage.toString());
        }
    }

    public String sendPost(String uri, String jsonPayload) {
        return executeHttpRequest(uri, "POST", jsonPayload, false);
    }

    public String sendPostWithPatchContentType(String uri, String jsonPayload) {
        return executeHttpRequest(uri, "POST", jsonPayload, true);
    }

    public String sendPatch(String uri, String jsonPayload) {
        return executeHttpRequest(uri, "PATCH", jsonPayload, true);
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

    public AzureDevopsRestConfig getConfig() {
        return restConfig;
    }

}
