package com.ppm.integration.agilesdk.connector.azuredevops.model;

import com.google.gson.JsonElement;

public class ConnectionData extends AzureDevopsObject {
    private JsonElement authenticatedUser;

    private JsonElement authorizedUser;

    private String instanceId;

    public JsonElement getAuthenticatedUser() {
        return authenticatedUser;
    }

    public void setAuthenticatedUser(JsonElement authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    public JsonElement getAuthorizedUser() {
        return authorizedUser;
    }

    public void setAuthorizedUser(JsonElement authorizedUser) {
        this.authorizedUser = authorizedUser;
    }

    public String getInstanceId() {
        return instanceId;
    }

    public void setInstanceId(String instanceId) {
        this.instanceId = instanceId;
    }
}
