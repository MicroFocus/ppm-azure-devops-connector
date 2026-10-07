
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops.rest;


import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;
import org.apache.commons.lang3.StringUtils;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.HttpHost;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.StreamUtils;


import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

/** Unlike the other AgileSDK connectors that use Wink REST Client, Azure DevOps uses Spring HTTP client abstractions because
 * Azure DevOps REST API requires HTTP PATCH to update work items, and Java HttpUrlConnection (used in Wink REST Client) doesn't support it...
 * */
public class AzureDevopsRestClient {

    private final static Logger logger = LogManager.getLogger(AzureDevopsRestClient.class);
    private static final int REQUEST_TIMEOUT_MILLIS = 60_000;

    private AzureDevopsRestConfig restConfig;


    public AzureDevopsRestClient(AzureDevopsRestConfig config) {
        this.restConfig = config;
    }

    // Azure DevOps REST API needs a very specific content-type when doing PATCH.
    private String executeHttpRequest(String relativeUrl, String httpMethod, String jsonPayload, boolean usePatchJsonContentType) {

        String fullUrl = restConfig.getOrganizationUrl() + relativeUrl;

        logger.debug("url: " + fullUrl);

        ClientHttpResponse response = null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));
            headers.set(HttpHeaders.AUTHORIZATION, restConfig.getBasicAuthorizationHeaderValue());
            // Following header is required for easy HTTP request tracing in systems such as DataPower.
            headers.set("X-B3-TraceId", UUID.randomUUID().toString());

            if (jsonPayload != null) {
                headers.setContentType(usePatchJsonContentType
                        ? MediaType.parseMediaType("application/json-patch+json;charset=UTF-8")
                        : new MediaType(MediaType.APPLICATION_JSON, StandardCharsets.UTF_8));
            }

            ClientHttpRequest request = buildHttpRequest(fullUrl, httpMethod, headers, jsonPayload);
            response = request.execute();
            ResponseEntity<String> responseEntity = ResponseEntity.status(response.getStatusCode()).headers(response.getHeaders()).body(readResponseBody(response));

            // All Azure DevOps REST calls should return HTTP 200 status code if successful.
            checkResponseStatus(200, responseEntity, fullUrl, httpMethod, jsonPayload);

            return responseEntity.getBody() != null ? responseEntity.getBody() : "";
        } catch (Exception e) {
            throw new RuntimeException("Error occurred when making REST call to " + fullUrl, e);
        } finally {
            if (response != null) {
                try {
                    response.close();
                } catch (Exception e) {
                    // Do nothing.
                }
            }
        }

    }

    public String sendGet(String uri) {
        return executeHttpRequest(uri, "GET", null, false);
    }

    private ClientHttpRequestFactory createRequestFactory() {
        HttpClientBuilder httpClientBuilder = HttpClients.custom()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                        .setResponseTimeout(REQUEST_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
                        .build());
        if (!StringUtils.isBlank(restConfig.getProxyHost())) {
            httpClientBuilder.setProxy(new HttpHost("http", restConfig.getProxyHost(), restConfig.getProxyPort()));
        }
        return  new HttpComponentsClientHttpRequestFactory(httpClientBuilder.build());
    }

    private ClientHttpRequest buildHttpRequest(String fullUrl, String httpMethod, HttpHeaders headers, String body) throws java.io.IOException {
        ClientHttpRequest request = createRequestFactory().createRequest(URI.create(fullUrl), HttpMethod.valueOf(httpMethod.toUpperCase(Locale.ROOT)));

        for (String headerName : headers.headerNames()) {
            for (String headerValue : headers.getValuesAsList(headerName)) {
                request.getHeaders().add(headerName, headerValue);
            }
        }

        if (body != null) {
            StreamUtils.copy(body, StandardCharsets.UTF_8, request.getBody());
        }

        return request;
    }

    private String readResponseBody(ClientHttpResponse response) throws java.io.IOException {
        return response.getBody() != null ? StreamUtils.copyToString(response.getBody(), StandardCharsets.UTF_8) : "";
    }

    private void checkResponseStatus(int expectedHttpStatusCode, ResponseEntity<String> response, String uri, String verb, String payload) {

        if (response.getStatusCode().value() != expectedHttpStatusCode) {
            StringBuilder errorMessage = new StringBuilder(String.format("## Unexpected HTTP response status code %s for %s uri %s, expected %s", response.getStatusCode().value(), verb, uri, expectedHttpStatusCode));

            if (payload != null) {
                errorMessage.append(System.lineSeparator()).append(System.lineSeparator()).append("# Sent Payload:").append(System.lineSeparator()).append(payload);
            }
            String responseStr = response.getBody();
            if (!StringUtils.isBlank(responseStr)) {
                errorMessage.append(System.lineSeparator()).append(System.lineSeparator()).append("# Received Response:").append(System.lineSeparator()).append(responseStr);
            }

            throw new RestRequestException(response.getStatusCode().value(), errorMessage.toString());
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
