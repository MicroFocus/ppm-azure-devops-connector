package com.ppm.integration.agilesdk.connector.azuredevops.util;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/** This class helps building a WIQL (Work Item query language.
 * This is important as all Work items details can be only retrieved with their ID and in batches of 200 max. Only a WIQL allows you to retrieve up to 20000 work items ids.
 *
 * Default building implementation will only include work item ID column, and has no specific search criteria.
 * */
public class WIQLBuilder {

    private Set<String> columns = new HashSet<>();

    private Set<String> workItemTypes = new HashSet<>();

    private Set<String> excludedStatuses = new HashSet<>();

    private boolean crossProjectsSearch = false;

    /**
     * Default implementation - only returns Work Items IDs.
     */
    public WIQLBuilder() {
        columns.add("System.Id");
    }

    /**
     * This method is useless today, because Azure DevOps WIQL result will only return the work item ID.
     * @param columnIdentifier the identifier of the column without the square brackets, for example: System.Title */
    public WIQLBuilder addColumn(String columnIdentifier) {
        columns.add(columnIdentifier);
        return this;
    }

    /**
     * Limits returned work items to the specified workItemType(s). You can call this method multiple times to include multiple work item types.
     * By default, all work items types are returned.
     */
    public WIQLBuilder setReturnedWorkItemType(Collection<String> workItemTypes) {
        this.workItemTypes.addAll(workItemTypes);
        return this;
    }


    public WIQLBuilder addStatusesToExclude(String... statusesToExclude) {
        if (statusesToExclude != null && statusesToExclude.length > 0) {
            excludedStatuses.addAll(Arrays.asList(statusesToExclude));
        }
        return this;
    }

    public WIQLBuilder crossProjectsSearch(boolean crossProjectsSearch) {
        this.crossProjectsSearch = crossProjectsSearch;
        return this;
    }

    public String build() {
        StringBuilder sb = new StringBuilder("SELECT ");
        // Including all columns
        sb.append(StringUtils.join(columns.stream().map(c -> {return "["+c+"]";}).collect(Collectors.toList()), ", "));
        sb.append(" FROM workitems ");

        boolean needAnd = false;
        if (!crossProjectsSearch) {
            needAnd = addWhereAnd(needAnd, sb);
            sb.append(" [System.TeamProject] = @project ");
        }
        if (!excludedStatuses.isEmpty()) {
            needAnd = addWhereAnd(needAnd, sb);
            sb.append(StringUtils.join(excludedStatuses.stream().map(status -> " [State] <> '"+status+"' ").collect(Collectors.toList()), " AND "));
        }

        if (workItemTypes != null && !workItemTypes.isEmpty()) {

            needAnd = addWhereAnd(needAnd, sb);

            sb.append(" (");
            sb.append(StringUtils.join(workItemTypes.stream().map(wit -> {return " [System.WorkItemType] = '"+wit+"' ";}).collect(Collectors.toList()), " OR "));
            sb.append(") ");
        }

        // No sorting criteria for now.

        return sb.toString();
    }

    private boolean addWhereAnd(boolean needAnd, StringBuilder sb) {
        if (needAnd) {
            sb.append(" AND ");
        } else {
            sb.append(" WHERE ");
        }
        return true;
    }
}
