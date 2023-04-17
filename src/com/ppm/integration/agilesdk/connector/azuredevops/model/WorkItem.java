package com.ppm.integration.agilesdk.connector.azuredevops.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hp.ppm.user.model.User;
import com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsConstants;
import com.ppm.integration.agilesdk.provider.UserProvider;
import org.apache.commons.lang3.StringUtils;

import java.lang.reflect.Method;
import java.util.*;

import static com.ppm.integration.agilesdk.connector.azuredevops.util.AzureDevOpsUtils.parseDateStr;

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

    public Date getLastUpdateTime() {
        Date lastUpdateTime = getDateField("System.ChangedDate");
        if (lastUpdateTime == null) {
            throw new RuntimeException("System.ChangedDate field cannot be retrieved on this work item - Last update date is mandatory");
        }
        return lastUpdateTime;
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

    public String getStringField(String fieldName) {
        JsonElement fieldValue = getFields().get(fieldName);

        if (fieldValue == null || !fieldValue.isJsonPrimitive()) {
            return null;
        }

        return fieldValue.getAsString();
    }

    public Double getNumberField(String fieldName) {
        JsonElement fieldValue = getFields().get(fieldName);

        if (fieldValue == null || !fieldValue.isJsonPrimitive()) {
            return null;
        }

        return fieldValue.getAsDouble();
    }

    public Date getDateField(String fieldName) {
        String dateStr = getStringField(fieldName);

        if (org.apache.commons.lang.StringUtils.isBlank(dateStr)) {
            return null;
        }

        return parseDateStr(dateStr);
    }

    /**
     * @return The list of the PPM User IDs based on the content of the emails or people for that property.
     *
     *
     */
    public List<User> getResourceField(String fieldName, Long projectId, UserProvider userProvider) {

        List<User> ppmResources = new ArrayList<>();

        JsonElement fieldValue = getFields().get(fieldName);

        if (fieldValue == null) {
            return ppmResources;
        }

        if (fieldValue.isJsonArray()) {
            Set<Long> ppmResourceIds = new HashSet<>();
            for (JsonElement val : fieldValue.getAsJsonArray()) {
                User user = getResourceIdFromEmailOrUsernameOrFullName(getUserIdentifierFromElement(val), getFullNameFromElement(val), userProvider, projectId);
                if (user != null && !ppmResourceIds.contains(user.getUserId())) {
                    ppmResourceIds.add(user.getUserId());
                    ppmResources.add(user);
                }
            }
        } else {
            User user = getResourceIdFromEmailOrUsernameOrFullName(getUserIdentifierFromElement(fieldValue), getFullNameFromElement(fieldValue), userProvider, projectId);
            if (user != null) {
                ppmResources.add(user);
            }
        }

        return ppmResources;
    }

    private String getFullNameFromElement(JsonElement fieldValue) {
        if (fieldValue.isJsonPrimitive()) {
            return fieldValue.getAsString();
        }

        if (fieldValue.isJsonObject()) {
            JsonObject field = fieldValue.getAsJsonObject();
            if (field.has("displayName") && field.get("displayName").isJsonPrimitive()) {
                return field.get("displayName").getAsString();
            }
        }

        return null;
    }

    private String getUserIdentifierFromElement(JsonElement fieldValue) {
        if (fieldValue.isJsonPrimitive()) {
            return fieldValue.getAsString();
        }

        if (fieldValue.isJsonObject()) {
            JsonObject field = fieldValue.getAsJsonObject();
            if (field.has("uniqueName") && field.get("uniqueName").isJsonPrimitive()) {
                return field.get("uniqueName").getAsString();
            }
            if (field.has("name") && field.get("name").isJsonPrimitive()) {
                return field.get("name").getAsString();
            }
        }

        return null;
    }

    private User getResourceIdFromEmailOrUsernameOrFullName(String emailOrUsername, String fullName, UserProvider userProvider, Long projectId) {
        User user = null;

        if (!org.apache.commons.lang.StringUtils.isBlank(emailOrUsername)) {
            user = userProvider.getByEmail(emailOrUsername.trim());

            if (user == null) {
                user = userProvider.getByUsername(emailOrUsername.trim());
            }
        }

        if (user == null && !org.apache.commons.lang.StringUtils.isBlank(fullName)) {
            // This code is complicated and use reflection because we want it to work on older versions of PPM where
            // UserProvider doesn't have the #getByFullName" method, which was introduced in PPM 2023.3
            try {
                Method m = UserProvider.class.getMethod("getByFullName", String.class, Long.class, boolean.class);
                if (m != null) {
                    user = (User) m.invoke(userProvider, fullName.trim(), projectId, false);
                }
            } catch (Exception e) {
                // We do nothing, the method doesn't exist
            }

            // Above reflection code will just call this on PPM 2023.3+:
            // user = userProvider.getByFullName(fullName.trim(), projectId, false);
        }
       return user;
    }
}
