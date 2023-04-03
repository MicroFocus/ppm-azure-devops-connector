package com.ppm.integration.agilesdk.connector.azuredevops.model;

import com.google.gson.JsonObject;

import java.util.List;

public class WorkItemType extends AzureDevopsObject {

    private String description;

    private String referenceName;

    private boolean isDisabled;

    private List<Field> fields;

    private List<Field> fieldInstances;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getReferenceName() {
        return referenceName;
    }

    public void setReferenceName(String referenceName) {
        this.referenceName = referenceName;
    }

    public boolean isDisabled() {
        return isDisabled;
    }

    public void setDisabled(boolean disabled) {
        isDisabled = disabled;
    }

    public List<Field> getFields() {
        return fields;
    }

    public void setFields(List<Field> fields) {
        this.fields = fields;
    }

    public List<Field> getFieldInstances() {
        return fieldInstances;
    }

    public void setFieldInstances(List<Field> fieldInstances) {
        this.fieldInstances = fieldInstances;
    }
}
