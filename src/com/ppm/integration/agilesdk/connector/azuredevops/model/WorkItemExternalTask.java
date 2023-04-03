package com.ppm.integration.agilesdk.connector.azuredevops.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.hp.ppm.integration.service.impl.ProjectUtilService;
import com.hp.ppm.user.model.User;
import com.kintana.core.logging.LogManager;
import com.kintana.core.logging.Logger;
import com.mercury.itg.core.impl.SpringContainerFactory;
import com.ppm.integration.agilesdk.ValueSet;
import com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsConstants;
import com.ppm.integration.agilesdk.connector.azuredevops.service.AzureDevopsService;
import com.ppm.integration.agilesdk.connector.azuredevops.util.AzureDevOpsUtils;
import com.ppm.integration.agilesdk.pm.ExternalTask;
import com.ppm.integration.agilesdk.pm.ExternalTaskActuals;
import com.ppm.integration.agilesdk.pm.WorkPlanIntegrationContext;
import com.ppm.integration.agilesdk.provider.UserProvider;
import org.apache.commons.lang.StringUtils;

import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static com.ppm.integration.agilesdk.connector.azuredevops.util.AzureDevOpsUtils.parseDateStr;

/**
 * Exposes a AzureDevops Page object as an External Task, based on the passed config.
 */
public class WorkItemExternalTask extends ExternalTask {

    private final static Logger logger = LogManager.getLogger(WorkItemExternalTask.class);

    private WorkItem workItem;
    private ValueSet config;
    private UserProvider userProvider;
    private Double effort = 0.0d;
    private List<Long> resourcesIds = new ArrayList<>();

    private List<ExternalTask> children = new ArrayList<>();



    private Iteration iteration = null;


    private boolean isWorkLeafTask = false;

    public WorkItemExternalTask(WorkItem workItem, ValueSet config, UserProvider userProvider, WorkPlanIntegrationContext context, AzureDevopsService service) {
        this.workItem = workItem;
        this.config = config;
        this.userProvider = userProvider;
        this.resourcesIds = getResourceField("System.AssignedTo", getProjectIdFromContext(context));
        this.effort = getNumberField("Microsoft.VSTS.Scheduling.Effort");
        this.iteration = service.getIteration(getStringField("System.IterationPath"));
    }

    public String getIterationPath() {
        return iteration == null ? null : iteration.getPath();
    }

    private Long getProjectIdFromContext(WorkPlanIntegrationContext context) {
        // There seems to be a bug to retrieve project ID when synching project from work plan, so we get project ID from task ID.
        if (context != null && context.currentTask() != null) {
            return ((ProjectUtilService) SpringContainerFactory.getBean("projectUtilService")).getWorkPlan(context.currentTask().getWorkplanId()).getProject().getId();
        }
        return null;
    }

    @Override
    public List<ExternalTask> getChildren() {
        return children;
    }

    @Override
    public TaskStatus getStatus() {

        String status = getAzureDevopsStatus();

        Set<String> inProgressStatuses = AzureDevOpsUtils.extractStringListParams(config.get(AzureDevopsConstants.KEY_WP_IN_PROGRESS_STATUSES)).stream().map(String::toLowerCase).collect(Collectors.toSet());
        Set<String> completedStatuses = AzureDevOpsUtils.extractStringListParams(config.get(AzureDevopsConstants.KEY_WP_CLOSED_STATUSES)).stream().map(String::toLowerCase).collect(Collectors.toSet());

        if (inProgressStatuses.contains(status.toLowerCase())) {
            return TaskStatus.IN_PROGRESS;
        } else if (completedStatuses.contains(status.toLowerCase())) {
            return TaskStatus.COMPLETED;
        } else {
            return TaskStatus.READY;
        }
    }

    @Override
    public String getId() {
        return workItem.id;
    }

    @Override
    public String getName() {
        return (isWorkLeafTask ? "[Work] " : "[" + getStringField("System.WorkItemType") + "]") + getStringField("System.Title");
    }

    @Override
    public Date getScheduledStart() {
        Date startDate = adjustStartDateTime(getDateField("Microsoft.VSTS.Scheduling.StartDate"));

        if (startDate == null && iteration != null) {
            startDate = adjustStartDateTime(parseDateStr(iteration.getAttributes().getStartDate()));
        }

        if (startDate == null) {
            startDate = adjustStartDateTime(getDefaultStartDate());
        }

        return startDate;
    }


    @Override
    public Date getScheduledFinish() {
        Date finishDate = adjustStartDateTime(getDateField("Microsoft.VSTS.Scheduling.TargetDate"));

        if (finishDate == null && iteration != null) {
            finishDate = adjustStartDateTime(parseDateStr(iteration.getAttributes().getFinishDate()));
        }

        if (finishDate == null) {
            finishDate = adjustStartDateTime(getDefaultFinishDate());
        }

        return finishDate;
    }

    @Override
    public List<ExternalTaskActuals> getActuals() {

        List<ExternalTaskActuals> actuals = new ArrayList<ExternalTaskActuals>();

        double effortValue = effort == null ? 0d: effort;

        final double numResources = resourcesIds.size();

        if (resourcesIds.isEmpty()) {
            // All is unassigned effort
            ExternalTaskActuals unassignedActuals = new AzureDevopsExternalTaskActuals(effortValue, getStatus(), getScheduledStart(), getScheduledFinish(), null);
            actuals.add(unassignedActuals);
        } else {
            // One Actual entry per resource.
            for (final Long resourceId : resourcesIds) {
                ExternalTaskActuals resourceActuals = new AzureDevopsExternalTaskActuals(effortValue / numResources, getStatus(), getScheduledStart(), getScheduledFinish(), resourceId);
                actuals.add(resourceActuals);
            }
        }

        return actuals;
    }

    private String getStringField(String fieldName) {
        JsonElement fieldValue = workItem.getFields().get(fieldName);

        if (fieldValue == null || !fieldValue.isJsonPrimitive()) {
            return null;
        }

        return fieldValue.getAsString();
    }

    private Double getNumberField(String fieldName) {
        JsonElement fieldValue = workItem.getFields().get(fieldName);

        if (fieldValue == null || !fieldValue.isJsonPrimitive()) {
            return null;
        }

        return fieldValue.getAsDouble();
    }

    private Date getDateField(String fieldName) {
        String dateStr = getStringField(fieldName);

        if (StringUtils.isBlank(dateStr)) {
            return null;
        }

        return parseDateStr(dateStr);
    }



    /**
     * @return The list of the PPM User IDs based on the content of the emails or people for that property.
     *
     *
     */
    public List<Long> getResourceField(String fieldName, Long projectId) {

        List<Long> ppmResourceIds = new ArrayList<>();

        JsonElement fieldValue = workItem.getFields().get(fieldName);

        if (fieldValue == null) {
            return ppmResourceIds;
        }

        if (fieldValue.isJsonArray()) {
            for (JsonElement val : fieldValue.getAsJsonArray()) {
                Long userId = getResourceIdFromEmailOrUsernameOrFullName(getUserIdentifierFromElement(val), getFullNameFromElement(val), userProvider, projectId);
                if (userId != null && !ppmResourceIds.contains(userId)) {
                    ppmResourceIds.add(userId);
                }
            }
        } else {
            Long userId = getResourceIdFromEmailOrUsernameOrFullName(getUserIdentifierFromElement(fieldValue), getFullNameFromElement(fieldValue), userProvider, projectId);
            if (userId != null) {
                ppmResourceIds.add(userId);
            }
        }

        return ppmResourceIds;
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

    private Long getResourceIdFromEmailOrUsernameOrFullName(String emailOrUsername, String fullName, UserProvider userProvider, Long projectId) {
        User user = null;

        if (!StringUtils.isBlank(emailOrUsername)) {
            user = userProvider.getByEmail(emailOrUsername.trim());

            if (user == null) {
                user = userProvider.getByUsername(emailOrUsername.trim());
            }
        }

        if (user == null && !StringUtils.isBlank(fullName)) {
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
        if (user == null) {
            return null;
        } else {
            return user.getUserId();
        }
    }

    public void addChild(ExternalTask child) {
        children.add(child);
    }

    public void addChildFirst(ExternalTask child) {
        children.add(0, child);
    }

    public void setIsWorkLeafTask(boolean b) {
        this.isWorkLeafTask = true;
    }

    public Double getEffort() {
        return effort;
    }

    public WorkItem getWorkItem() {
        return workItem;
    }

    public String getWorkItemId() {
        return workItem.getId();
    }

    public String getAzureDevopsStatus() {
        String status = getStringField("System.State");
        if (status == null) {
            status = "New";
        }

        return status;
    }

    public Iteration getIteration() {
        return iteration;
    }

    private Date getDefaultStartDate() {
        Calendar todayMorning = new GregorianCalendar();
        todayMorning.set(Calendar.HOUR, 1);
        todayMorning.set(Calendar.MINUTE, 0);
        todayMorning.set(Calendar.SECOND, 0);
        todayMorning.set(Calendar.MILLISECOND, 0);
        return todayMorning.getTime();

    }

    private Date getDefaultFinishDate() {
        Calendar todayEvening = new GregorianCalendar();
        todayEvening.set(Calendar.HOUR, 23);
        todayEvening.set(Calendar.MINUTE, 0);
        todayEvening.set(Calendar.SECOND, 0);
        todayEvening.set(Calendar.MILLISECOND, 0);
        return todayEvening.getTime();
    }

}



