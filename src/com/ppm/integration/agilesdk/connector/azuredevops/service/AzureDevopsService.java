package com.ppm.integration.agilesdk.connector.azuredevops.service;

import com.google.gson.*;
import com.ppm.integration.agilesdk.connector.azuredevops.AzureDevopsConstants;
import com.ppm.integration.agilesdk.connector.azuredevops.model.*;
import com.ppm.integration.agilesdk.connector.azuredevops.rest.AzureDevopsRestClient;
import com.ppm.integration.agilesdk.connector.azuredevops.util.WIQLBuilder;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.wink.client.ClientResponse;

import java.util.*;

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
    public AzureDevopsService(AzureDevopsRestClient restClient) {
        this.restClient = restClient;
    }

    // Various cached info - these will only be cached in a given service instance, which provides limited (but existing) benefit.
    // Every REST call from UI will result in a new instance of this service.

    private Map<String, List<WorkItemType>> projectToWorkItemTypesCache = new HashMap<>();

    private Map<String, List<Iteration>> projectIterationsCache = new HashMap<>();

    public List<Project> getAllAvailableProjects() {

        ClientResponse response = restClient.sendGet(AzureDevopsConstants.API_PROJECTS_URL);

        return responseToListOf(Project.class, response);
    }

    private <T extends AzureDevopsObject> List<T> responseToListOf(Class<T> returnedClass, ClientResponse response) {
        List<T> result = new ArrayList<>();
        JsonElement listResponse = JsonParser.parseString(response.getEntity(String.class));
        JsonArray values = listResponse.getAsJsonObject().get("value").getAsJsonArray();
        Gson gson = new Gson();
        for (JsonElement value : values) {
            T obj = gson.fromJson(value, returnedClass);
            result.add(obj);
        }

        return result;
    }

    public List<WorkItem> getAllWorkItems(String projectId, String... statusesToExclude) {

        // We first retrieve the list of all work items IDs by using WIQL, and then we retrieve work items details in batch of 200.
        String wiql = new WIQLBuilder().addStatusesToExclude(statusesToExclude).build();
        List<Long> workItemIds = runWIQL(wiql, projectId);

        List<WorkItem> workItems = getWorkItemsByIds(workItemIds);

        return workItems;
    }

    public List<WorkItem> getProjectWorkItems(String projectId, Collection<String> workItemTypes, String... statusesToExclude) {

        // We first retrieve the list of all work items IDs by using WIQL, and then we retrieve work items details in batch of 200.
        String wiql = new WIQLBuilder().addStatusesToExclude(statusesToExclude).setReturnedWorkItemType(workItemTypes).build();
        List<Long> workItemIds = runWIQL(wiql, projectId);

        List<WorkItem> workItems = getWorkItemsByIds(workItemIds);

        return workItems;
    }

    /**
     * Retrieve details of work items by ID, in batches of 200.
     */
    private List<WorkItem> batchBreakdownWorkItemsById(List<Long> workItemIds) {

        if (workItemIds == null || workItemIds.isEmpty()) {
            return new ArrayList<>();
        }

        List<WorkItem> workItems = new ArrayList<>(workItemIds.size());

        List<List<Long>> batchedIds = com.google.common.collect.Lists.partition(workItemIds, WORK_ITEMS_BATCH_SIZE);

        for (List<Long> workItemIdsBatch : batchedIds) {
            List<WorkItem> workItemsBatch = getWorkItemsByIds(workItemIdsBatch);
            workItems.addAll(workItemsBatch);
        }

        return workItems;
    }

    /**
     * We do not include the project in this call - we assume all work items to belong to the same project, but they don't have to.
     */
    private List<WorkItem> getWorkItemsByIds(List<Long> workItemIds) {

        if (workItemIds == null || workItemIds.isEmpty()) {
            return new ArrayList<>();
        }

        if (workItemIds.size() > WORK_ITEMS_BATCH_SIZE) {
            return batchBreakdownWorkItemsById(workItemIds);
        }

        String workItemsRelativeUrl = AzureDevopsConstants.API_WORK_ITEMS_URL + "&$expand=relations&ids="+StringUtils.join(workItemIds, ",");

        return responseToListOf(WorkItem.class, restClient.sendGet(workItemsRelativeUrl));
    }

    /** Runs a WIQL and returns the list of matching work items ids.
     * Project is mandatory, as it will help ensure that no more than 20000 work items are matching the query (hard max limit).
     */
    private List<Long> runWIQL(String wiql, String projectId) {
        String wiqlRelativeUrl = "/" + projectId + AzureDevopsConstants.API_WIQL_SUFFIX_URL;

        String payload = "{\n" +
                "  \"query\": \""+wiql+"\"\n" +
                "}";

        ClientResponse response = restClient.sendPost(wiqlRelativeUrl, payload, 200);

        JsonElement listResponse = JsonParser.parseString(response.getEntity(String.class));
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


}
