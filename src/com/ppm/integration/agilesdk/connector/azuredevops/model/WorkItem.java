package com.ppm.integration.agilesdk.connector.azuredevops.model;

import com.google.gson.JsonObject;
import com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsConstants;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class WorkItem extends AzureDevopsObject {

    public String getProjectId() {
        if (projectId == null) {
            // Extract project id from self-link
            projectId = extractProjectIdFromUrl(getUrl());
        }

        return projectId;
    }

    public String getParentProjectId() {
        return extractProjectIdFromUrl(getParentUrl());
    }

    public String getParentWorkItemId() {
        String parentUrl = getParentUrl();
        if (!StringUtils.isBlank(parentUrl)) {
            return parentUrl.substring(parentUrl.lastIndexOf('/')+1);
        }
        return null;
    }

    private String getParentUrl() {
        if (getRelations() != null) {
            for (Relation relation : getRelations()) {
                if (AzureDevopsConstants.RELATION_PARENT_REL.equals(relation.getRel())) {
                    return relation.getUrl();
                }
            }
        }

        return null;
    }

    private String extractProjectIdFromUrl(String url) {
        if (StringUtils.isBlank(url)) {
            return null;
        }
        String noAPIUrl = url.substring(0, url.indexOf(AzureDevopsConstants.APIS_URL));
        return noAPIUrl.substring(noAPIUrl.lastIndexOf('/') + 1);
    }



    private String projectId = null;

    private Integer rev;

    private JsonObject fields;

    public List<Relation> relations;

    public Integer getRev() {
        return rev;
    }

    public void setRev(Integer rev) {
        this.rev = rev;
    }

    public JsonObject getFields() {
        return fields;
    }

    public void setFields(JsonObject fields) {
        this.fields = fields;
    }

    public List<Relation> getRelations() {
        return relations;
    }

    public void setRelations(List<Relation> relations) {
        this.relations = relations;
    }

    public class Relation {
        private String rel;
        private String url;

        public String getRel() {
            return rel;
        }

        public void setRel(String rel) {
            this.rel = rel;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        // We don't need attributes for now.
    }
}
