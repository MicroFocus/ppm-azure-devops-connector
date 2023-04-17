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
    private List<User> resources = new ArrayList<>();

    private List<ExternalTask> children = new ArrayList<>();



    private Iteration iteration = null;


    private boolean isWorkLeafTask = false;

    public WorkItemExternalTask(WorkItem workItem, ValueSet config, WorkPlanIntegrationContext context, AzureDevopsService service) {
        this.workItem = workItem;
        this.config = config;
        this.userProvider = service.getUserProvider();
        this.resources = this.workItem.getResourceField("System.AssignedTo", getProjectIdFromContext(context), userProvider);
        this.effort = this.workItem.getNumberField("Microsoft.VSTS.Scheduling.Effort");
        this.iteration = service.getIteration(this.workItem.getStringField("System.IterationPath"));
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
        return (isWorkLeafTask ? "[Work] " : "[" + this.workItem.getStringField("System.WorkItemType") + "]") + this.workItem.getStringField("System.Title");
    }

    @Override
    public Date getScheduledStart() {
        Date startDate = adjustStartDateTime(this.workItem.getDateField("Microsoft.VSTS.Scheduling.StartDate"));

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
        Date finishDate = adjustStartDateTime(this.workItem.getDateField("Microsoft.VSTS.Scheduling.TargetDate"));

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

        final double numResources = resources.size();

        if (resources.isEmpty()) {
            // All is unassigned effort
            ExternalTaskActuals unassignedActuals = new AzureDevopsExternalTaskActuals(effortValue, getStatus(), getScheduledStart(), getScheduledFinish(), null);
            actuals.add(unassignedActuals);
        } else {
            // One Actual entry per resource.
            for (final User resource : resources) {
                ExternalTaskActuals resourceActuals = new AzureDevopsExternalTaskActuals(effortValue / numResources, getStatus(), getScheduledStart(), getScheduledFinish(), resource.getUserId());
                actuals.add(resourceActuals);
            }
        }

        return actuals;
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
        String status = this.workItem.getStringField("System.State");
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



