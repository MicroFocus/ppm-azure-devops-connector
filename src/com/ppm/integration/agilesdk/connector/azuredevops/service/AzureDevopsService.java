package com.ppm.integration.agilesdk.connector.azuredevops.service;

import com.google.gson.*;
import com.hp.ppm.common.model.AgileEntityIdName;
import com.hp.ppm.common.model.AgileEntityIdProjectDate;
import com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsConstants;
import com.ppm.integration.agilesdk.connector.azuredevops.model.*;
import com.ppm.integration.agilesdk.connector.azuredevops.rest.AzureDevopsRestClient;
import com.ppm.integration.agilesdk.connector.azuredevops.util.WIQLBuilder;
import com.ppm.integration.agilesdk.dm.DataField;
import com.ppm.integration.agilesdk.dm.User;
import com.ppm.integration.agilesdk.provider.UserProvider;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;


import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Class in charge of making calls to AzureDevops REST API when needed.
 * Contains a cache, so the service should not be a static member of a class, as the caches are never invalidated and might contain stale data if used as such.
 *
 * This class not thread safe.
 */
public class AzureDevopsService {

    private final static Logger logger = Logger.getLogger(AzureDevopsService.class);
    private static final int WORK_ITEMS_BATCH_SIZE = 200;

    private AzureDevopsRestClient restClient;

    private UserProvider userProvider = null;

    public AzureDevopsService(AzureDevopsRestClient restClient) {
        this.restClient = restClient;
    }

    // Various cached info - these will only be cached in a given service instance, which provides limited (but existing) benefit.
    // Every REST call from UI will result in a new instance of this service.

    private Map<String, List<WorkItemType>> projectToWorkItemTypesCache = new HashMap<>();

    private Map<String, List<Iteration>> projectIterationsCache = new HashMap<>();

    /** Map<projectId_workItemTypeId, List<Field>> */
    private Map<String, List<Field>> workItemTypeFieldsCache = new HashMap<>();

    public WorkItem createWorkItem(String projectId, String entityType) {
        String createWorkItemUrl =  "/"+projectId + AzureDevopsConstants.API_WORK_ITEMS_END_POINT + "/$" + entityType
                + AzureDevopsConstants.VERSION_7_VERSION_SUFFIX +"&bypassRules=true";

        // Azure DevOps update works by sending an op for each field that is modified.
        String payload = "[{" +
                "\"op\" : \"add\"," +
                "\"path\" : \"/fields/System.Title\"," +
                "\"from\" : null," +
                "\"value\" : \"Work Item created from PPM\"" +
                "}]";


        return responseTo(WorkItem.class, restClient.sendPostWithPatchContentType(createWorkItemUrl, payload));
    }

    public List<Project> getAllAvailableProjects() {

        String response = restClient.sendGet(AzureDevopsConstants.API_PROJECTS_URL);

        return responseToListOf(Project.class, response);
    }

    private <T extends AzureDevopsObject> List<T> responseToListOf(Class<T> returnedClass, String response) {
        List<T> result = new ArrayList<>();
        JsonElement listResponse = JsonParser.parseString(response);
        JsonArray values = listResponse.getAsJsonObject().get("value").getAsJsonArray();
        Gson gson = new Gson();
        for (JsonElement value : values) {
            T obj = gson.fromJson(value, returnedClass);
            result.add(obj);
        }

        return result;
    }

    private <T extends AzureDevopsObject> T responseTo(Class<T> returnedClass, String response) {
        T obj = new Gson().fromJson(response, returnedClass);
        return obj;
    }

    public List<WorkItem> getAllWorkItems(String projectId, String... statusesToExclude) {

        // We first retrieve the list of all work items IDs by using WIQL, and then we retrieve work items details in batch of 200.
        List<Long> workItemIds = runWIQL(new WIQLBuilder().addStatusesToExclude(statusesToExclude), projectId);

        List<WorkItem> workItems = getWorkItemsByIds(workItemIds);

        return workItems;
    }

    public List<WorkItem> getProjectWorkItems(String projectId, Collection<String> workItemTypes, String... statusesToExclude) {

        // We first retrieve the list of all work items IDs by using WIQL, and then we retrieve work items details in batch of 200.
        List<Long> workItemIds = runWIQL(new WIQLBuilder().addStatusesToExclude(statusesToExclude).setReturnedWorkItemType(workItemTypes), projectId);

        List<WorkItem> workItems = getWorkItemsByIds(workItemIds);

        return workItems;
    }

    /**
     * Retrieve details of work items by ID, in batches of 200.
     */
    private List<WorkItem> batchBreakdownWorkItemsById(List<Long> workItemIds, String...fieldsToInclude) {

        if (workItemIds == null || workItemIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<WorkItem> workItems = new ArrayList<>(workItemIds.size());

        List<List<Long>> batchedIds = com.google.common.collect.Lists.partition(workItemIds, WORK_ITEMS_BATCH_SIZE);

        for (List<Long> workItemIdsBatch : batchedIds) {
            List<WorkItem> workItemsBatch = getWorkItemsByIds(workItemIdsBatch, fieldsToInclude);
            workItems.addAll(workItemsBatch);
        }

        return workItems;
    }

    /**
     * We do not include the project in this call - we assume all work items to belong to the same project, but they don't have to.
     *
     * if fieldsToInclude is empty, all fields are returned.
     */
    private List<WorkItem> getWorkItemsByIds(List<Long> workItemIds, String...fieldsToInclude) {

        if (workItemIds == null || workItemIds.isEmpty()) {
            return new ArrayList<>();
        }

        if (workItemIds.size() > WORK_ITEMS_BATCH_SIZE) {
            return batchBreakdownWorkItemsById(workItemIds, fieldsToInclude);
        }

        String workItemsRelativeUrl = AzureDevopsConstants.API_WORK_ITEMS_URL + "&ids="+StringUtils.join(workItemIds, ",");

        // We cannot use both "expand" and "fields" parameter. So if specific fields are needed, we don't care about relations.
        if (fieldsToInclude != null && fieldsToInclude.length > 0) {
            workItemsRelativeUrl += "&fields="+StringUtils.join(fieldsToInclude, ',');
        } else {
            workItemsRelativeUrl += "&$expand=relations";
        }

        return responseToListOf(WorkItem.class, restClient.sendGet(workItemsRelativeUrl));
    }

    /** Runs a WIQL and returns the list of matching work items ids.
     * Project is mandatory, as it will help ensure that no more than 20000 work items are matching the query (hard max limit).
     */
    private List<Long> runWIQL(WIQLBuilder wiql, String projectId) {
        String wiqlRelativeUrl = "/" + projectId + AzureDevopsConstants.API_WIQL_SUFFIX_URL;

        if (wiql.needsTime()) {
            wiqlRelativeUrl +=  "&timePrecision=true";
        }

        String payload = "{\n" +
                "  \"query\": \""+wiql.build()+"\"\n" +
                "}";

        String response = restClient.sendPost(wiqlRelativeUrl, payload);

        JsonElement listResponse = JsonParser.parseString(response);
        JsonArray workItems = listResponse.getAsJsonObject().get("workItems").getAsJsonArray();
        List<Long> workItemIds = new ArrayList<>(workItems.size());
        for (JsonElement workItem : workItems) {
            workItemIds.add(workItem.getAsJsonObject().get("id").getAsLong());
        }

        return workItemIds;
    }

    public List<WorkItemType> getWorkItemTypesForProject(String projectId) {

        if (StringUtils.isBlank(projectId)) {
            // This will happen when work plan mapping screen is first loaded and no project is selected.
            return new ArrayList<>();
        }

        List<WorkItemType> wits = projectToWorkItemTypesCache.get(projectId);

        if (wits != null) {
            return wits;
        }

        // Make REST call to get info.
        String workItemTypesRelativeUrl = "/"+projectId + AzureDevopsConstants.API_WORK_ITEM_TYPES_URL;

        wits = responseToListOf(WorkItemType.class, restClient.sendGet(workItemTypesRelativeUrl));

        projectToWorkItemTypesCache.put(projectId, wits);

        return wits;
    }

    public Iteration getIteration(String iterationPath) {
        if (StringUtils.isBlank(iterationPath) || !(iterationPath.contains("\\"))) {
            return null;
        }

        String projectKey = iterationPath.substring(0, iterationPath.indexOf('\\'));

        List<Iteration> projectIterations = projectIterationsCache.get(projectKey);
        if (projectIterations == null) {
            // Loading project Iterations with REST call and caching them.
            String iterationsRelativeUrl = "/"+projectKey + AzureDevopsConstants.API_ITERATIONS_URL;

            projectIterations = responseToListOf(Iteration.class, restClient.sendGet(iterationsRelativeUrl));

            projectIterationsCache.put(projectKey, projectIterations);
        }

        for (Iteration iteration : projectIterations) {
            if (iterationPath.equalsIgnoreCase(iteration.getPath())) {
                return iteration;
            }
        }

        return null;
    }


    /**
     * Getting list of Fields in Azure DevOps requires two REST calls:
     * - One on the Work Item Type end point to get the fields of this work item type and the possible values (list)
     * - One on the "Fields" end point to get the field type and extra info.
     */
    public List<Field> getFieldsDetails(String projectId, String workItemTypeId) {

        List<Field> witFields  = workItemTypeFieldsCache.get(projectId+"_"+workItemTypeId);

        if (witFields != null) {
            return witFields;
        }

        // First we get fields from work item type (to have allowed Values)
        String workItemTypeFieldsUrl = "/"+projectId + "/"  + AzureDevopsConstants.API_WORK_ITEM_TYPES_ENDPOINT + "/"+workItemTypeId
                + "/fields" + AzureDevopsConstants.VERSION_7_VERSION_SUFFIX + "&$expand=allowedValues";
        witFields  = responseToListOf(Field.class, restClient.sendGet(workItemTypeFieldsUrl));

        // Then we get fields details to get type
        String fieldsDetailsUrl = "/"+projectId + AzureDevopsConstants.API_FIELDS_URL;

        List<Field> detailedFields = responseToListOf(Field.class, restClient.sendGet(fieldsDetailsUrl));
        Map<String, Field> detailedFieldByReferenceName = detailedFields.stream().collect(Collectors.toMap(Field::getReferenceName, Function.identity()));

        // We now specify the type of all work item type fields.
        for (Field f : witFields) {
            Field detailedField = detailedFieldByReferenceName.get(f.getReferenceName());
            if (detailedField != null) {
                f.setType(detailedField.getType());
            }
        }

        workItemTypeFieldsCache.put(projectId+"_"+workItemTypeId, witFields);

        return witFields;
    }

    public WorkItem updateWorkItem(String projectId, String workItemId, Iterator<Map.Entry<String, DataField>> fields) {
        String updateWorkItemUrl =  "/"+projectId + "/"  + AzureDevopsConstants.API_WORK_ITEMS_END_POINT + "/" + workItemId
                + AzureDevopsConstants.VERSION_7_VERSION_SUFFIX + "&$expand=relations&bypassRules=true";

        // Azure DevOps update works by sending an op for each field that is modified.
        JsonArray payload = new JsonArray();
        while (fields.hasNext()) {
            Map.Entry<String, DataField> field = fields.next();
            JsonObject op = new JsonObject();
            op.addProperty("path", "/fields/"+field.getKey());
            if(field.getValue() == null || field.getValue().get() == null){
                op.addProperty("op", "remove");
            }else{
                op.addProperty("op", "replace");
                setValuePropertyFromDataField(op, field.getValue());
            }
            payload.add(op);
        }
        return responseTo(WorkItem.class, restClient.sendPatch(updateWorkItemUrl, payload.toString()));
    }

    private void setValuePropertyFromDataField(JsonObject o, DataField field) {
        if (field == null || field.get() == null) {
            o.addProperty("value", (String)null);
            return;
        }

        switch (field.getType()) {
            case USER:
                if (field.isList()) {
                    List<User> users = (List<User>) field.get();
                    o.addProperty("value", users.stream().map(user -> user.getFullName()).collect(Collectors.joining(";")));
                } else {
                    User user = (User) field.get();
                    o.addProperty("value", user.getFullName());
                }
                return;
            case FLOAT:
                o.addProperty("value", (Float) field.get());
                return;
            case INTEGER:
                o.addProperty("value", (Long) field.get());
                return;
            default: //  String
                o.addProperty("value", field.get().toString());
                return;
        }
    }

    public String getWorkItemUrl(String id) {
        // We don't need to include the project in URL, Azure DevOps only needs the ID and it will redirect to the right project.
        return restClient.getConfig().getOrganizationUrl() + "/_workitems/edit/" + id;
    }

    public List<WorkItem> getWorkItemsModifiedSince(String projectId, String workItemType, List<String> workItemIds, Date modifiedSinceDate) {

        WIQLBuilder wiql = new WIQLBuilder().setReturnedWorkItemType(Arrays.asList(new String[]{workItemType})).filterByModifiedAfter(modifiedSinceDate).filterByIds(workItemIds);

        List<Long> validIds = runWIQL(wiql, projectId);

        return getWorkItemsByIds(validIds);
    }

    /**
     * User Provider cached in this service instance.
     * @return
     */
    public UserProvider getUserProvider() {
        if (this.userProvider == null) {
            this.userProvider = AzureDevopsServiceProvider.getUserProvider();
        }
        return this.userProvider;
    }

    public WorkItem getSingleWorkItem(String workItemId) {
        if (StringUtils.isBlank(workItemId)) {
            return null;
        }
        // We leverage the API to get multiple WI instead of the dedicated one for a single work item.
        List<WorkItem> wis = getWorkItemsByIds(Arrays.asList(new Long[] {Long.valueOf(workItemId)}));

        if (wis.isEmpty()) {
            return null;
        } else {
            return wis.get(0);
        }
    }

    public List<AgileEntityIdProjectDate> getAgileEntityIdsCreatedSince(String projectId, String workItemType, Date createdSinceDate) {
        WIQLBuilder wiql = new WIQLBuilder().setReturnedWorkItemType(Arrays.asList(new String[]{workItemType})).filterByCreatedAfter(createdSinceDate);

        List<Long> validIds = runWIQL(wiql, projectId);

        return getWorkItemsByIds(validIds, "System.CreatedDate").stream()
                .map(wi -> new AgileEntityIdProjectDate(wi.getId(), projectId, wi.getDateField("System.CreatedDate")))
                .collect(Collectors.toList());
    }

    public List<AgileEntityIdName> getAgileEntityIdsAndNames(String projectId, String workItemType) {
        List<Long> validIds = runWIQL(new WIQLBuilder().setReturnedWorkItemType(Arrays.asList(new String[]{workItemType})), projectId);

        return getWorkItemsByIds(validIds, "System.Title").stream()
                .map(wi -> new AgileEntityIdName(wi.getId(), wi.getStringField("System.Title")))
                .collect(Collectors.toList());
    }

    public String testConnection() {
        try {
            ConnectionData data = responseTo(ConnectionData.class, restClient.sendGet(AzureDevopsConstants.API_CONNECTION_DATA_URL));
        } catch (Exception e) {
            logger.error("Error when testing connectivity of azure devops connector", e);
            return e.getMessage();
        }
        // No error = return null.
        return null;
    }


    public List<WorkItem> getAllWorkItemsInfoFromProject(String projectId, Collection<String> workItemTypes, String... statusesToExclude) {
        // We first retrieve the list of all work items IDs by using WIQL, and then we retrieve work items details in batch of 200.
        List<Long> workItemIds = runWIQL(new WIQLBuilder().addStatusesToExclude(statusesToExclude).setReturnedWorkItemType(workItemTypes), projectId);

        // We only need the name & work item type info here.
        List<WorkItem> workItems = getWorkItemsByIds(workItemIds, "System.Title", "System.WorkItemType", "System.State");

        workItems.forEach(wi -> {wi.setName(wi.getStringField("System.Title"));});

        return workItems;

    }

    public List<WorkItem> getProjectWorkItemAndChildren(String projectId, String specificWorkItemId, Set<String> workItemTypes, String... statusesToExclude) {
        // We get the children recursively until there's no more children to return.
        List<Long> workItemIds = runDescendantLinksWIQL(projectId, specificWorkItemId, workItemTypes, statusesToExclude);

        List<WorkItem> workItems = getWorkItemsByIds(workItemIds);

        return workItems;
    }

    private List<Long> runDescendantLinksWIQL(String projectId, String specificWorkItemId, Set<String> workItemTypes, String[] statusesToExclude) {
        String wiqlRelativeUrl = "/" + projectId + AzureDevopsConstants.API_WIQL_SUFFIX_URL;

        String wiql = "Select [System.Id] From WorkItemLinks Where ( [System.Links.LinkType] = 'System.LinkTypes.Hierarchy-Forward' and Source.[System.Id] = "+specificWorkItemId;

        if (statusesToExclude != null && statusesToExclude.length > 0) {
            wiql += " and ("+org.apache.commons.lang3.StringUtils.join(Arrays.asList(statusesToExclude).stream().map(status -> " Target.[State] <> '"+status+"' ").collect(Collectors.toList()), " AND ")
            + ") ";
        }

        if (workItemTypes != null && !workItemTypes.isEmpty()) {

            wiql += " and ("+org.apache.commons.lang3.StringUtils.join(workItemTypes.stream().map(wit -> {return " Target.[System.WorkItemType] = '"+wit+"' ";}).collect(Collectors.toList()), " OR ")
            + ") ";
        }


        wiql += ") mode (Recursive)";

        String payload = "{\n" +
                "  \"query\": \""+wiql+"\"\n" +
                "}";

        String response = restClient.sendPost(wiqlRelativeUrl, payload);

        JsonElement listResponse = JsonParser.parseString(response);
        JsonArray workItemRelations = listResponse.getAsJsonObject().get("workItemRelations").getAsJsonArray();
        List<Long> workItemIds = new ArrayList<>(workItemRelations.size());
        for (JsonElement workItemRelation : workItemRelations) {
            if (workItemRelation.isJsonObject() && !workItemRelation.getAsJsonObject().isJsonNull()
                    && workItemRelation.getAsJsonObject().has("target")) {
                JsonElement target = workItemRelation.getAsJsonObject().get("target");
                if (target != null && !target.isJsonNull() && target.isJsonObject() && target.getAsJsonObject().has("id")) {
                    workItemIds.add(target.getAsJsonObject().get("id").getAsLong());
                }
            }
        }

        return workItemIds;
    }
}
