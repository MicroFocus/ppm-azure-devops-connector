
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
        return "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAACAAAAAgCAYAAABzenr0AAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAAEnQAABJ0Ad5mH3gAAANNSURBVFhH7ZdNbIxBGMefebeiqK5GSJsGcSC0PspFIkQ4KAnR0hAkDiLdtgkJaTk5EBdtg4SgDtycfCQSunUQFxIRrWpSiUhoVYiDapUI9h2/eeeV7rbb7btbjUv/ycw888zH/7/zzs4zoyQTRKIiyjkvohvlcmmX780Ijl+mCyXaXS2u2yZHO8qltt13p49MBfjQeQi5KY5zQY51ZvvOtJDeJ7BLH4Z0r2h9yhMQLvQbVTtCdsvpJS99RyAEE1B5j56hlSJuRLTsgTjHb5FBAR6+IfAQAq9Kw3LflRqpBUSiU8l3QQixXmWdQ5AowEKp62TVUr+03/ckova56WOsacMFVDazM0KL+RWGdJ+3zKmQTIAH9RqSPczzlEoOcy5iviLsIspifjqlzBsUEImyIdVOu8x6HZ5gnydBgNKMes/ErZC3UX/CDzjDfAspk254S1J5Fyt0HLUnvXogeGRvJLfwmUemnFbRsTbIPnnNf/dA3YvvkE+xleGwAiItGxh8Hyvk1YdBxcheQdRKacjaxPXIeiVcINK4wuuVFKMKqGop4EBhYp3vu36SddolNEsJmY61s6wDcmWz1yMtjCqgsvkBHdbbmqKzzITsR0ZkyTCKADaGT26hpf/DvyMPgDEexWPHhIAJARMCjABtzf8DR5ysORy3BzgFb3EU9kt2WDie/ebxR3w4JlOTyOoJySZud1M3N95uxGFLF8GHUOsSKxgW9LQMFA3jUdWSR3AycXyB74mD+k32kdTFSCswXPiWEpFOt7i/jNCv3ldtKDEDMhBgEGkpJgI+xsq1jhQYeiER+WzFIdII0241tlnZpEguwCASLWfwDazU/5QRr2RBoDpSTO7cZlm5eo8TlDKbbu3IApo2kqkTiLhjHR7MErvWHAOUc4l9tlUalvWlXt6mUkPGzVjZx4Zyerndzqc8TOURKV0xMcYegbxGGkvMhg5wEjZt6uPFUw7ZF6+u9Tt85zDWIGYuEx6k7SHJ3BtTYYC+27lLnpXGwUfLyJtwKCLRLeTXSLMRkHh61vHQcLLyEVdGqsDDtV5n2UYD1cM338ZDxVxqExBcwH4OysnTa/gFFxHgO5PAvJSd0CwElJEq2DUzIN8BeY/fIw4ifwCQwhM+O+NkXQAAAABJRU5ErkJggg==";
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
