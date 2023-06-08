
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import com.ppm.integration.agilesdk.FunctionIntegration;
import com.ppm.integration.agilesdk.IntegrationConnector;
import com.ppm.integration.agilesdk.ValueSet;
import com.ppm.integration.agilesdk.connector.azuredevops.model.Project;
import com.ppm.integration.agilesdk.connector.azuredevops.service.AzureDevopsServiceProvider;
import com.ppm.integration.agilesdk.model.AgileProject;
import com.ppm.integration.agilesdk.ui.*;

/**
 * Main Connector class file for Jira Cloud connector.
 * Note that the Jira Cloud version is purely informative - there is no version for Jira Cloud.
 */
public class AzureDevopsIntegrationConnector extends IntegrationConnector {

    @Override
    public String getExternalApplicationName() {
        return "Azure Devops";
    }

    @Override
    public String getExternalApplicationVersionIndication() {
        return "2023+";
    }

    @Override
    public String getConnectorVersion() {
        return "0.3";
    }

    @Override
    public String getTargetApplicationIcon() {
        return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAABAAAAAQCAYAAAAf8/9hAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAAEnMAABJzAYwiuQcAAAF+SURBVDhPzZO9TgJBFIXPLMbGghgLC02EwsQoYqmlHYaCBhMLS6PLC+BPa2ECsbFDbXwDO4UHsDIsoA+gUBiNRhOJFcJ4ZmY3Kyz+dX7J3Ttz586Zm5m7Al9hF9X3GOGxHeRnn3SsD5br+zEBKdOQnSqyV4tuLEB3BXYxBMgEN2Y4S9JCrECttCHEHtN3Wc27CmiytZARsEujQGcNEusUiOiYhxFQ3EFYBxRXVcdo08ydEtw8DNkuMxBVWUTypFv6Cs2hgAPLcpCLPejVbK3F74AeE4GN81PuSXG4xTIr2gqJZ3c9SFDgjCeihaPkoAn9QI/Ad6/wK/6HwAkv7t5M/47XB/Ryn2+8wFmDVtde0IfH63z/BnIzryo1+AoedmmI/XDB0ZwJuPiN9MJ0iss4x96+N19AYRej7P1LjkZMgPgCvbAzRar7Eg+Xbii+Qmuy7En6bZoSVL3ymSrX55GPl7sr8MiUMryPAgXNfPM6ov9MyGUKPvLkVf5UTQD4AMwgbro1X8+GAAAAAElFTkSuQmCC";
    }

    @Override
    public List<Field> getDriverConfigurationFields() {
        return Arrays.asList(new Field[]{
                new PlainText(AzureDevopsConstants.KEY_PROXY_HOST, "PROXY_HOST", "", false),
                new PlainText(AzureDevopsConstants.KEY_PROXY_PORT, "PROXY_PORT", "", false),
                new LineBreaker(),
                new PlainText(AzureDevopsConstants.KEY_ORGANIZATION_URL, "LABEL_ORGANIZATION_URL", "", true),
                new LineBreaker(),
                new LabelText("", "AUTHENTICATION_SETTINGS_SECTION", "block", false),
                new PasswordText(AzureDevopsConstants.KEY_PERSONAL_ACCESS_TOKEN, "LABEL_PERSONAL_ACCESS_TOKEN", "", true),
                new LineBreaker(),
                new LabelText("", "WORKPLAN_CONFIG_SECTION", "block", false),
                new PlainText(AzureDevopsConstants.KEY_WP_IMPORTABLE_WORK_ITEM_TYPES, "LABEL_WP_IMPORTABLE_WORK_ITEM_TYPES", "Epic;Feature;User Story;Task", true),
                new PlainText(AzureDevopsConstants.KEY_WP_IN_PROGRESS_STATUSES, "LABEL_WP_IN_PROGRESS_STATUSES", "Active;In Progress;Committed;Open;Doing", false),
                new PlainText(AzureDevopsConstants.KEY_WP_CLOSED_STATUSES, "LABEL_WP_CLOSED_STATUSES", "Done;Closed;Inactive;Completed;Resolved", false),
                new PlainText(AzureDevopsConstants.KEY_WP_IGNORED_STATUSES, "LABEL_WP_IGNORED_STATUSES", "Removed", false)
                //new CheckBox(AzureDevopsConstants.KEY_FORCE_INTEGRATION_TOKEN_USE, "LABEL_FORCE_INTEGRATION_TOKEN_USE", false)
        });
    }

    @Override
    public List<AgileProject> getAgileProjects(ValueSet instanceConfigurationParameters) {

        List<Project> projects = AzureDevopsServiceProvider.get(instanceConfigurationParameters).getAllAvailableProjects();

        return projects.stream().map(project -> {
            AgileProject proj = new AgileProject();
            proj.setValue(project.getId());
            proj.setDisplayName(project.getName());
            return proj;
        }).collect(Collectors.toList());

    }

    @Override
    public List<FunctionIntegration> getIntegrations() {
        return Arrays.asList(new FunctionIntegration[]{new AzureDevopsWorkPlanIntegration()});
    }

    @Override
    public List<String> getIntegrationClasses() {
        return Arrays.asList(new String[]{"com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsWorkPlanIntegration", "com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsRequestIntegration"});
    }

    @Override
    public String testConnection(ValueSet instanceConfigurationParameters) {
        return AzureDevopsServiceProvider.get(instanceConfigurationParameters).testConnection();
    }

}
