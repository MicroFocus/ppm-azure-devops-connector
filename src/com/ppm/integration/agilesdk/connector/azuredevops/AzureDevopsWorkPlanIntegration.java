
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops;

import com.ppm.integration.agilesdk.ValueSet;
import com.ppm.integration.agilesdk.connector.azuredevops.model.*;
import com.ppm.integration.agilesdk.connector.azuredevops.service.AzureDevopsService;
import com.ppm.integration.agilesdk.connector.azuredevops.service.AzureDevopsServiceProvider;
import com.ppm.integration.agilesdk.connector.azuredevops.util.AzureDevOpsUtils;
import com.ppm.integration.agilesdk.pm.*;
import com.ppm.integration.agilesdk.provider.LocalizationProvider;
import com.ppm.integration.agilesdk.provider.Providers;
import com.ppm.integration.agilesdk.provider.UserProvider;
import com.ppm.integration.agilesdk.ui.*;
import com.ppm.integration.agilesdk.ui.Field;
import org.apache.log4j.Logger;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class AzureDevopsWorkPlanIntegration extends WorkPlanIntegration {


    private final Logger logger = Logger.getLogger(AzureDevopsWorkPlanIntegration.class);

    public AzureDevopsWorkPlanIntegration() {
    }

    private AzureDevopsService service;

    private synchronized AzureDevopsService getService(ValueSet config) {
        if (service == null) {
            service = AzureDevopsServiceProvider.get(config);
        }
        return service;
    }

    @Override
    public List<Field> getMappingConfigurationFields(WorkPlanIntegrationContext context, ValueSet values) {

        final LocalizationProvider lp = Providers.getLocalizationProvider(AzureDevopsIntegrationConnector.class);

        List<Field> fields = new ArrayList<>();

        DynamicDropdown projectsList = new DynamicDropdown(AzureDevopsConstants.KEY_WP_PROJECT, "WP_PROJECT", true) {
            @Override
            public List<String> getDependencies() {
                return Arrays.asList(new String[]{AzureDevopsConstants.KEY_PERSONAL_ACCESS_TOKEN});
            }

            @Override
            public List<Option> getDynamicalOptions(ValueSet values) {
                final List<Project> projects = getService(values).getAllAvailableProjects();
                Collections.sort(projects, (o1, o2) -> o1.getName().compareToIgnoreCase(o2.getName()));
                List<Option> options = new ArrayList<>();
                projects.stream().forEach(proj -> options.add(new DynamicDropdown.Option(proj.getId(), proj.getName())));
                return options;
            }
        };


        fields.add(new LabelText("LABEL_PROJECT_TO_SYNC", "LABEL_PROJECT_TO_SYNC",
                "Select what project to import:", true));


        fields.add(projectsList);

        SelectList importGroupSelectList = new SelectList(AzureDevopsConstants.KEY_IMPORT_GROUPS,"IMPORT_GROUPS",AzureDevopsConstants.GROUP_STRUCTURE,true)
                .addLevel(AzureDevopsConstants.KEY_IMPORT_GROUPS, "IMPORT_GROUPS")
                .addOption(new SelectList.Option(AzureDevopsConstants.GROUP_STRUCTURE, "GROUP_STRUCTURE"))
                .addOption(new SelectList.Option(AzureDevopsConstants.GROUP_SPRINT,"GROUP_SPRINT"))
                .addOption(new SelectList.Option(AzureDevopsConstants.GROUP_STATUS,"GROUP_STATUS"));

        fields.add(importGroupSelectList);

        fields.add(new LineBreaker());

        fields.add(new CheckBox(AzureDevopsConstants.KEY_WP_INCLUDE_CLOSED, "LABEL_INCLUDE_CLOSED", false));

        fields.add(new LineBreaker());
        fields.add(new LabelText("", "LABEL_WP_IMPORTED_WORK_ITEM_TYPES", "block", false));

        Collection<String> importableWorkItemTypes = AzureDevOpsUtils.extractStringListParams(values.get(AzureDevopsConstants.KEY_WP_IMPORTABLE_WORK_ITEM_TYPES));

        final AzureDevopsService service = AzureDevopsServiceProvider.get(values);

        // All Work Items types to be imported. Only the ones that exist in the selected project (process) will be selectable.
        for (final String workItemType : importableWorkItemTypes) {


            // No default work item is selected for now, because even when disabled they show as checked and it can be confusing.
            boolean isChecked = false;


            fields.add(new CheckBox(AzureDevopsConstants.WP_WORK_ITEM_TYPE_PREFIX + workItemType.replace(" ", "__"), workItemType, isChecked) {

                @Override
                public List<String> getStyleDependencies() {
                    return Arrays.asList(new String[]{AzureDevopsConstants.KEY_WP_PROJECT});
                }

                @Override
                public FieldAppearance getFieldAppearance(ValueSet values) {
                    String projectKey = values.get(AzureDevopsConstants.KEY_WP_PROJECT);

                    List<WorkItemType> projectWorkItemTypes = service.getWorkItemTypesForProject(projectKey);

                    Set<String> enabledWorkItemTypeNames = projectWorkItemTypes.stream().filter(wit -> !wit.isDisabled()).map(wit -> wit.getName()).collect(Collectors.toSet());

                    if (projectWorkItemTypes != null && enabledWorkItemTypeNames.contains(workItemType)) {
                        // This work item type is enabled for this project process.
                        return new FieldAppearance("", "disabled");
                    } else {
                        // This work item type is disabled for this project process
                        return new FieldAppearance("disabled", "");
                    }
                }
            });
        }

        return fields;
    }


    @Override
    /**
     * This method is in Charge of retrieving all AzureDevops DB rows and turning them into a workplan structure to be imported in PPM.
     */
    public ExternalWorkPlan getExternalWorkPlan(WorkPlanIntegrationContext context, final ValueSet values) {

        final String projectId = values.get(AzureDevopsConstants.KEY_WP_PROJECT);

        List<String> statusesToIgnore = new ArrayList<>();

        final boolean includeClosed = values.getBoolean(AzureDevopsConstants.KEY_WP_INCLUDE_CLOSED, false);

        if (!includeClosed) {
            statusesToIgnore.addAll(AzureDevOpsUtils.extractStringListParams(values.get(AzureDevopsConstants.KEY_WP_CLOSED_STATUSES)));
        }

        statusesToIgnore.addAll(AzureDevOpsUtils.extractStringListParams(values.get(AzureDevopsConstants.KEY_WP_IGNORED_STATUSES)));

        Set<String> workItemTypes = new HashSet<>();

        for (String key: values.keySet()) {
            if (key.startsWith(AzureDevopsConstants.WP_WORK_ITEM_TYPE_PREFIX)) {
                if (values.getBoolean(key, false)) {
                    workItemTypes.add(getWorkItemTypeNameFromParamName(key));
                }
            }
        }

        final AzureDevopsService runService = getService(values);
        final UserProvider userProvider = service.getUserProvider();

        final List<WorkItem> workItems = runService.getProjectWorkItems(projectId, workItemTypes, statusesToIgnore.toArray(new String[statusesToIgnore.size()]));

        // We first create all External Tasks, but without any structure (children) info.
        Map<String, WorkItemExternalTask> externalTasksByWorkItemIds = new LinkedHashMap<>(workItems.size());
        for (WorkItem wi : workItems) {
            WorkItemExternalTask externalTask = new WorkItemExternalTask(wi, values, context, runService);
            externalTasksByWorkItemIds.put(wi.getId(), externalTask);
        }

        // Root tasks that will be returned eventually.
        final List<ExternalTask> rootTasks = new ArrayList<>();
        if (AzureDevopsConstants.GROUP_STATUS.equals(values.get(AzureDevopsConstants.KEY_IMPORT_GROUPS))) {
            // Group by status
            rootTasks.addAll(getRootTasksGroupByStatus(externalTasksByWorkItemIds, values));
        } else if (AzureDevopsConstants.GROUP_SPRINT.equals(values.get(AzureDevopsConstants.KEY_IMPORT_GROUPS))) {
            // Group by sprint
            rootTasks.addAll(getRootTasksGroupBySprint(externalTasksByWorkItemIds, service, values));
        } else {
            // Group by work items structure.
            rootTasks.addAll(groupWorkItemExternalTasksByStructure(externalTasksByWorkItemIds.values(), values));
        }

        // In order to properly reflect the date and effort for summary tasks work items, We add a child leaf work item (no child) for any summary task work item.
        for (Map.Entry<String, WorkItemExternalTask> et : externalTasksByWorkItemIds.entrySet()) {
            if (!et.getValue().getChildren().isEmpty()) {
                // Summary task! If there's effort, we add itself as a [Work] child to reflect effort.
                if (et.getValue().getEffort() != null && et.getValue().getEffort() > 0) {
                    WorkItemExternalTask externalTaskLeaf = new WorkItemExternalTask(et.getValue().getWorkItem(), values, context, runService);
                    externalTaskLeaf.setIsWorkLeafTask(true);
                    et.getValue().addChildFirst(externalTaskLeaf);
                }
            }
        }

        return new ExternalWorkPlan() {

            @Override
            public List<ExternalTask> getRootTasks() {
                return rootTasks;
            }
        };
    }

    private List <ExternalTask> getRootTasksGroupBySprint(Map<String, WorkItemExternalTask> externalTasksByWorkItemIds, AzureDevopsService service, ValueSet values) {

        List <ExternalTask> rootTasks = new ArrayList<>();
        Map<String, List<WorkItemExternalTask>> tasksByIterationPath = new LinkedHashMap<>(externalTasksByWorkItemIds.size());
        Map<String, Iteration> iterationsByPath = new HashMap<>();

        for (Map.Entry<String, WorkItemExternalTask> et : externalTasksByWorkItemIds.entrySet()) {
            String iterationPath  = et.getValue().getIterationPath();
            if (et.getValue().getIteration() != null) {
                iterationsByPath.put(iterationPath, et.getValue().getIteration());
            }
            List<WorkItemExternalTask> tasks = tasksByIterationPath.get(iterationPath);
            if (tasks ==  null) {
                tasks = new ArrayList<>();
                tasksByIterationPath.put(iterationPath, tasks);
            }

            tasks.add(et.getValue());
        }

        List<Iteration> sortedIterations = iterationsByPath.values().stream().sorted((o1, o2) -> (o1 == null || o1.getStartDate() == null) ? -1 : ((o2 == null || o2.getStartDate() == null) ? 1 : o1.getStartDate().compareTo(o2.getStartDate()))).collect(Collectors.toList());

        // create one external task for each sorted iteration
        for (Iteration iteration : sortedIterations) {
            final List<WorkItemExternalTask> children = groupWorkItemExternalTasksByStructure(tasksByIterationPath.get(iteration.getPath()), values);
            ExternalTask iterationTask = new ExternalTask() {
                @Override
                public String getId() {
                    return iteration.getPath();
                }

                @Override
                public String getName() {
                    return iteration.getName();
                }

                @Override
                public Date getScheduledStart() {
                    return iteration.getStartDate();
                }

                @Override
                public Date getScheduledFinish() {
                    return iteration.getFinishDate();
                }

                @Override
                public List<ExternalTask> getChildren() {
                    return children == null ? new ArrayList<>() : (List<ExternalTask>)(List<? extends ExternalTask>)children;
                }
            };

            rootTasks.add(iterationTask);
        }

        // We create one extra task for items without iteration (if any)
        if (tasksByIterationPath.get(null) != null && !tasksByIterationPath.get(null).isEmpty()) {
            final List<WorkItemExternalTask> children = groupWorkItemExternalTasksByStructure(tasksByIterationPath.get(null), values);
            ExternalTask noIterationTask = new ExternalTask() {
                @Override
                public String getId() {
                    return "no-iteration-task";
                }

                @Override
                public String getName() {
                    return "No Sprint/Iteration";
                }

                @Override
                public List<ExternalTask> getChildren() {
                    return (List<ExternalTask>)(List<? extends ExternalTask>)children;
                }
            };

            rootTasks.add(noIterationTask);
        }

        return rootTasks;
    }

    private List<ExternalTask> getRootTasksGroupByStatus(Map<String, WorkItemExternalTask> externalTasksByWorkItemIds, ValueSet values) {
        Map<String, List<WorkItemExternalTask>> tasksByStatus = new LinkedHashMap<>(externalTasksByWorkItemIds.size());

        for (Map.Entry<String, WorkItemExternalTask> et : externalTasksByWorkItemIds.entrySet()) {
            String status  = et.getValue().getAzureDevopsStatus();
            List<WorkItemExternalTask> tasks = tasksByStatus.get(status);
            if (tasks ==  null) {
                tasks = new ArrayList<>();
                tasksByStatus.put(status, tasks);
            }
            tasks.add(et.getValue());
        }

        List<ExternalTask> rootTasks = new ArrayList<>();

        for (Map.Entry<String, List<WorkItemExternalTask>> et : tasksByStatus.entrySet()) {

            final String statusName = et.getKey();

            final List<ExternalTask> children = (List<ExternalTask>)(List<? extends ExternalTask>)groupWorkItemExternalTasksByStructure(et.getValue(), values);

            ExternalTask statusTask = new ExternalTask() {
                @Override
                public String getName() {
                    return statusName;
                }

                @Override
                public List<ExternalTask> getChildren() {
                    return children;
                }
            };

            rootTasks.add(statusTask);
        }

        return rootTasks;
    }

    /**
     * We take the list of work item external tasks passed in parameter, structure them according to parent information,
     * then return the root tasks, i.e. the tasks without parent within the tasks provided as parameter.
     */
    private List<WorkItemExternalTask> groupWorkItemExternalTasksByStructure(Collection<WorkItemExternalTask> externalTasks, ValueSet config) {

        String projectId = config.get(AzureDevopsConstants.KEY_WP_PROJECT);

        List<WorkItemExternalTask> rootTasks = new ArrayList<>();

        Map<String, WorkItemExternalTask> externalTaskByWorkItemId = externalTasks.stream().collect(Collectors.toMap(WorkItemExternalTask::getWorkItemId, Function.identity()));

        // Now that we have all external tasks created + root tasks, we need to add children to build the hierarchy
        for (WorkItemExternalTask et : externalTasks) {
            String parentId = et.getWorkItem().getParentWorkItemId();
            if (parentId != null && externalTaskByWorkItemId.containsKey(parentId) && projectId.equalsIgnoreCase(et.getWorkItem().getParentProjectId())) {
                // Another imported work item is the parent - let's add this one as the child.
                externalTaskByWorkItemId.get(parentId).addChild(et);
            } else {
                // The parent is not imported, so we'll import this work item as a root task even if it has a parent defined in Azure DevOps
                rootTasks.add(et);
            }
        }

        return rootTasks;
    }

    private String getWorkItemTypeNameFromParamName(String paramName) {
        if (paramName == null) {
            return null;
        }
        return paramName.substring(AzureDevopsConstants.WP_WORK_ITEM_TYPE_PREFIX.length()).replace("__", " ");
    }

    /**
     * This will allow to have the information in PPM DB table PPMIC_WORKPLAN_MAPPINGS of what entity in JIRA is effectively linked to the PPM work plan task.
     * It is very useful for reporting purpose.
     *
     * @since 9.42
     */
    public LinkedTaskAgileEntityInfo getAgileEntityInfoFromMappingConfiguration(ValueSet values) {
        LinkedTaskAgileEntityInfo info = new LinkedTaskAgileEntityInfo();

        String projectId = values.get(AzureDevopsConstants.KEY_WP_PROJECT);

        info.setProjectId(projectId);

        // TODO more info, like Epic, if selected later.

        return info;
    }


    @Override
    public boolean supportTimesheetingAgainstExternalWorkPlan() {
        return true;
    }
}
