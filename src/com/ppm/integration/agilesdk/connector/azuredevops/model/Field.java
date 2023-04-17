package com.ppm.integration.agilesdk.connector.azuredevops.model;

import com.google.gson.JsonObject;

/** Field as defined in a work item type */
public class Field extends AzureDevopsObject {

    private String helpText;

    private boolean alwaysRequired;

    private String referenceName;

    private String type;

    private String[] allowedValues;

    public String getHelpText() {
        return helpText;
    }

    public void setHelpText(String helpText) {
        this.helpText = helpText;
    }

    public boolean isAlwaysRequired() {
        return alwaysRequired;
    }

    public void setAlwaysRequired(boolean alwaysRequired) {
        this.alwaysRequired = alwaysRequired;
    }

    public String getReferenceName() {
        return referenceName;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String[] getAllowedValues() {
        return allowedValues;
    }

    public void setAllowedValues(String[] allowedValues) {
        this.allowedValues = allowedValues;
    }
}
