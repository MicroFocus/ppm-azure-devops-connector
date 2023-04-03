package com.ppm.integration.agilesdk.connector.azuredevops.model;

public class Project extends AzureDevopsObject {

    private int revision;

    private String visibility;

    private String state;

    public int getRevision() {
        return revision;
    }

    public void setRevision(int revision) {
        this.revision = revision;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }
}
