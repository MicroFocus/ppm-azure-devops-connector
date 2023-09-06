
/*
 * © Copyright 2019 - 2020 Micro Focus or one of its affiliates.
 */

package com.ppm.integration.agilesdk.connector.azuredevops;

public class AzureDevopsConstants {

    public static final String KEY_PROXY_HOST = "proxyHost";

    public static final String KEY_PROXY_PORT = "proxyPort";

    public static final String KEY_PERSONAL_ACCESS_TOKEN = "personalAccessToken";

    public static final String KEY_ORGANIZATION_URL = "organizationUrl";
    public static final String API_ROOT_URL = "https://dev.azure.com/";



    public static final String APIS_URL = "/_apis/";

    public static final String API_CONNECTION_DATA_URL = APIS_URL + "ConnectionData";

    public static final String VERSION_7_VERSION_SUFFIX = "?api-version=7.0";
    public static final String VERSION_51_VERSION_SUFFIX = "?api-version=5.1";

    public static final String API_PROJECTS_URL = APIS_URL + "projects" + VERSION_7_VERSION_SUFFIX;

    public static final String API_WORK_ITEMS_END_POINT = APIS_URL + "wit/workitems" ;

    public static final String API_WORK_ITEMS_URL = API_WORK_ITEMS_END_POINT + VERSION_7_VERSION_SUFFIX;

    public static final String API_WORK_ITEM_TYPES_ENDPOINT = APIS_URL + "wit/workitemtypes";

    public static final String API_WORK_ITEM_TYPES_URL = API_WORK_ITEM_TYPES_ENDPOINT + VERSION_7_VERSION_SUFFIX;

    public static final String API_FIELDS_URL = APIS_URL + "wit/fields"+ VERSION_7_VERSION_SUFFIX;

    public static final String API_ITERATIONS_URL = APIS_URL + "work/teamsettings/iterations" + VERSION_7_VERSION_SUFFIX;

    public static final String API_WIQL_SUFFIX_URL = APIS_URL + "wit/wiql" + VERSION_7_VERSION_SUFFIX;

    public static final String WP_WORK_ITEM_TYPE_PREFIX = "WP_WIT_";


    public static final String DEVOPS_API_VERSION = "1";
    public static final String KEY_FORCE_INTEGRATION_TOKEN_USE = "forceIntegrationTokenUse";

    public static final String KEY_WP_PROJECT =  "wpProject";

    public static final String KEY_WP_EPIC =  "wpEpic";

    public static final String KEY_WP_EPIC_TYPES = "wpEpicTypes";

    public static final String KEY_WP_IMPORTABLE_WORK_ITEM_TYPES =  "wpImportableWorkItemTypes";

    public static final String KEY_WP_IGNORED_STATUSES =  "wpIgnoredStatuses";
    public static final String KEY_WP_CLOSED_STATUSES =  "wpClosedStatuses";

    public static final String KEY_WP_IN_PROGRESS_STATUSES =  "wpInProgressStatuses";



    public static final String KEY_WP_INCLUDE_CLOSED =  "wpIncludeClosed";
    public static final String RELATION_PARENT_REL = "System.LinkTypes.Hierarchy-Reverse";
    public static final String KEY_IMPORT_GROUPS = "importGroups";
    public static final String GROUP_STRUCTURE = "groupStructure";
    public static final String GROUP_SPRINT = "groupSprint";
    public static final String GROUP_STATUS = "groupStatus";
}
